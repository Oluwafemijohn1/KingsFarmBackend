package com.kingsfarm.kingsfarmbackend.wholeegg;

import com.kingsfarm.kingsfarmbackend.audit.Audited;
import com.kingsfarm.kingsfarmbackend.common.CatKey;
import com.kingsfarm.kingsfarmbackend.common.Mod;
import com.kingsfarm.kingsfarmbackend.common.PaymentMethod;
import com.kingsfarm.kingsfarmbackend.common.exception.BadRequestException;
import com.kingsfarm.kingsfarmbackend.common.exception.ForbiddenException;
import com.kingsfarm.kingsfarmbackend.common.exception.NotFoundException;
import com.kingsfarm.kingsfarmbackend.common.reports.ReportColumn;
import com.kingsfarm.kingsfarmbackend.common.reports.ReportPeriods;
import com.kingsfarm.kingsfarmbackend.common.reports.ReportTableResponse;
import com.kingsfarm.kingsfarmbackend.openingstock.OpeningStockLockService;
import com.kingsfarm.kingsfarmbackend.production.ProductionService;
import com.kingsfarm.kingsfarmbackend.systemlog.LogType;
import com.kingsfarm.kingsfarmbackend.wholeegg.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Backs WholeEggView in full: the customer directory (incl. one-time Opening
 * Balance seeding, BACKEND_PLAN.md §11 decision), Stock Overview, New Sale /
 * Record Payment, per-transaction same-day editing, and the Sales Crack &
 * Gift commits that feed Production. Credit/advance are never hand-typed
 * anywhere here — always derived from amountPaid vs. what's owed, mirroring
 * WholeEggView's submitSale/RecordPaymentModal/EditSaleTxnModal exactly.
 *
 * Note on field-level admin gating: enteredBy/updatedBy are included in
 * every response here regardless of caller role, same as Bird Stock and
 * Production so far — BACKEND_PLAN.md §3.4's "don't just hide it in the UI,
 * don't serialize it at all for non-admins" is intentionally deferred to the
 * Phase 6 hardening pass rather than retrofitted module-by-module mid-build.
 */
@Service
public class WholeEggService {

    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private com.kingsfarm.kingsfarmbackend.backfill.EditWindowService editWindow;

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final CustomerRepository customerRepository;
    private final WeCategoryValueRepository categoryValueRepository;
    private final WeSaleTransactionRepository transactionRepository;
    private final WeSaleLineItemRepository lineItemRepository;
    private final OpeningStockLockService lockService;
    private final ProductionService productionService;

    public WholeEggService(CustomerRepository customerRepository,
                            WeCategoryValueRepository categoryValueRepository,
                            WeSaleTransactionRepository transactionRepository,
                            WeSaleLineItemRepository lineItemRepository,
                            OpeningStockLockService lockService,
                            ProductionService productionService) {
        this.customerRepository = customerRepository;
        this.categoryValueRepository = categoryValueRepository;
        this.transactionRepository = transactionRepository;
        this.lineItemRepository = lineItemRepository;
        this.lockService = lockService;
        this.productionService = productionService;
    }

    // ── Category values (opening / price / sales-crack / gift) ─────────────

    private double categoryValue(WeCategoryValueKind kind, CatKey category) {
        return categoryValueRepository.findByKindAndCategory(kind, category).map(WeCategoryValue::getValue).orElse(0.0);
    }

    private Map<CatKey, Double> categoryMap(WeCategoryValueKind kind) {
        Map<CatKey, Double> map = new EnumMap<>(CatKey.class);
        for (CatKey k : CatKey.values()) {
            map.put(k, 0.0);
        }
        for (WeCategoryValue v : categoryValueRepository.findAllByKind(kind)) {
            map.put(v.getCategory(), v.getValue());
        }
        return map;
    }

    private void setCategoryValue(WeCategoryValueKind kind, CatKey category, double value) {
        WeCategoryValue entity = categoryValueRepository.findByKindAndCategory(kind, category)
                .orElseGet(() -> WeCategoryValue.builder().kind(kind).category(category).build());
        entity.setValue(value);
        categoryValueRepository.save(entity);
    }

    /** Public "public-facing crack use" reader for ProductionService's Whole Egg → Production feed (see wiring task). Fractional crates retained, not truncated. */
    @Transactional(readOnly = true)
    public double salesCrackFor(CatKey category) {
        return categoryValue(WeCategoryValueKind.SALES_CRACK, category);
    }

    @Transactional(readOnly = true)
    public double giftFor(CatKey category) {
        return categoryValue(WeCategoryValueKind.GIFT, category);
    }

    /** Sum of every SALE-type transaction's qty for a category — Production's auto-received "Total Sales". */
    @Transactional(readOnly = true)
    public double totalSalesFor(CatKey category) {
        return soldQtyByCategory(LocalDate.now()).getOrDefault(category, 0.0);
    }

    /** Sales for one specific day — Production's daily carry-forward needs the finished day's figure, not today's. */
    @Transactional(readOnly = true)
    public double totalSalesFor(CatKey category, LocalDate day) {
        return soldQtyByCategory(day).getOrDefault(category, 0.0);
    }

    private Map<CatKey, Double> soldQtyByCategory(LocalDate day) {
        Map<CatKey, Double> map = new EnumMap<>(CatKey.class);
        for (CatKey k : CatKey.values()) {
            map.put(k, 0.0);
        }
        for (WeSaleLineItemRepository.CategoryQtyProjection p : lineItemRepository.sumQtyByCategoryAndTypeBetween(WeSaleTxnType.SALE, com.kingsfarm.kingsfarmbackend.common.AppDay.start(day), com.kingsfarm.kingsfarmbackend.common.AppDay.end(day))) {
            map.put(p.getCategory(), p.getQty());
        }
        return map;
    }

    /**
     * Daily rollover: each category's finished-day closing becomes the new
     * opening (locked, like Bird Stock), and the day's committed crack/gift
     * figures start again at zero. Sales are already per-day (date-scoped sums).
     */
    @Transactional
    public void rollOver(LocalDate prevDay) {
        Map<CatKey, Double> opening = categoryMap(WeCategoryValueKind.OPENING);
        Map<CatKey, Double> crack = categoryMap(WeCategoryValueKind.SALES_CRACK);
        Map<CatKey, Double> gift = categoryMap(WeCategoryValueKind.GIFT);
        Map<CatKey, Double> production = productionService.catProdTotals(prevDay);
        Map<CatKey, Double> sold = soldQtyByCategory(prevDay);
        for (CatKey k : CatKey.values()) {
            double closing = opening.get(k) + production.getOrDefault(k, 0.0) - sold.getOrDefault(k, 0.0) - gift.get(k) - crack.get(k);
            setCategoryValue(WeCategoryValueKind.OPENING, k, closing);
            setCategoryValue(WeCategoryValueKind.SALES_CRACK, k, 0);
            setCategoryValue(WeCategoryValueKind.GIFT, k, 0);
            if (!editWindow.isCatchingUp(Mod.WHOLE_EGG)) lockService.lock(Mod.WHOLE_EGG, k.wire());
        }
    }

    @Audited(module = Mod.WHOLE_EGG, action = "Update Opening Stock", detail = "#request.category() + ' -> ' + #request.value()")
    @Transactional
    public void setOpening(UpdateCategoryValueRequest request, String username) {
        if (lockService.isLocked(Mod.WHOLE_EGG, request.category().wire())) {
            throw new ForbiddenException("Opening Stock is locked. Submit an Opening Stock request for the Administrator to approve.");
        }
        LocalDate day = request.date();
        if (day != null && day.isBefore(LocalDate.now())) {
            // The working day is a finished day: the opening lives in that day's Production row (one source of truth).
            if (!editWindow.isOpen(Mod.WHOLE_EGG, day)) {
                throw new ForbiddenException("This day isn't open for entry. Days must be entered in order - the banner at the top shows the day to work on. A day that is already closed needs the Administrator's permission.");
            }
            productionService.setOpeningForDay(request.category(), request.value(), day, username);
            return;
        }
        setCategoryValue(WeCategoryValueKind.OPENING, request.category(), request.value());
    }

    /** System adjustment (not a manager edit, so it ignores the opening-stock lock): shifts the live opening by a recalculation delta. */
    @Transactional
    public void adjustOpening(CatKey category, double delta) {
        setCategoryValue(WeCategoryValueKind.OPENING, category, categoryValue(WeCategoryValueKind.OPENING, category) + delta);
    }

    @Transactional
    public void lockOpening(CatKey category) {
        lockService.lock(Mod.WHOLE_EGG, category.wire());
    }

    @Audited(module = Mod.WHOLE_EGG, action = "Update Price", detail = "#request.category() + ' -> ' + #request.value()")
    @Transactional
    public void setPrice(UpdateCategoryValueRequest request) {
        // Not opening-stock-locked — role-restricted only, matching WholeEggView (prices are freely editable by the Whole Egg Manager).
        setCategoryValue(WeCategoryValueKind.PRICE, request.category(), request.value());
    }

    @Audited(module = Mod.WHOLE_EGG, action = "Commit Sales Crack")
    @Transactional
    public void commitSalesCrack(CommitCategoryQtyRequest request) {
        for (CategoryQtyEntry entry : request.values()) {
            setCategoryValue(WeCategoryValueKind.SALES_CRACK, entry.category(), entry.qty());
        }
    }

    @Audited(module = Mod.WHOLE_EGG, action = "Commit Gift")
    @Transactional
    public void commitGift(CommitCategoryQtyRequest request) {
        for (CategoryQtyEntry entry : request.values()) {
            setCategoryValue(WeCategoryValueKind.GIFT, entry.category(), entry.qty());
        }
    }

    // ── Stock Overview ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<StockRowResponse> stockRows() {
        Map<CatKey, Double> opening = categoryMap(WeCategoryValueKind.OPENING);
        Map<CatKey, Double> price = categoryMap(WeCategoryValueKind.PRICE);
        Map<CatKey, Double> salesCrack = categoryMap(WeCategoryValueKind.SALES_CRACK);
        Map<CatKey, Double> gift = categoryMap(WeCategoryValueKind.GIFT);
        // Production by Pen accepts partial crates (see ProductionService/
        // ProductionPenEntry's javadoc) and, per the Crate Quantity &
        // Conversion spec, Whole Egg's own Stock Overview must retain
        // fractional crates end-to-end too — no rounding boundary here
        // anymore (1 crate = 30 eggs, 0.5 crate = 15 eggs, etc.).
        Map<CatKey, Double> production = productionService.catProdTotals(LocalDate.now());
        Map<CatKey, Double> sales = soldQtyByCategory(LocalDate.now());

        List<StockRowResponse> rows = new ArrayList<>();
        for (CatKey k : CatKey.values()) {
            double op = opening.get(k);
            double prod = production.getOrDefault(k, 0.0);
            double sold = sales.getOrDefault(k, 0.0);
            double crack = salesCrack.get(k);
            double g = gift.get(k);
            double closing = op + prod - sold - g - crack;
            // PRICE is stored in the same double column but is always entered as a whole Naira amount — safe to narrow here.
            long p = price.get(k).longValue();
            rows.add(new StockRowResponse(k, op, prod, sold, crack, g, closing, p, lockService.isLocked(Mod.WHOLE_EGG, k.wire())));
        }
        return rows;
    }

    /** Stock table for the day being worked on: today = the live figures; a finished day = that day's own opening, production, sales and closing. */
    @Transactional(readOnly = true)
    public List<StockRowResponse> stockRows(LocalDate date) {
        if (date == null || !date.isBefore(LocalDate.now())) {
            return stockRows();
        }
        Map<CatKey, Double> price = categoryMap(WeCategoryValueKind.PRICE);
        List<StockRowResponse> rows = new ArrayList<>();
        for (com.kingsfarm.kingsfarmbackend.production.dto.CategoryStockRow r : productionService.categoryRowsFor(date)) {
            // Crack use and gifts for a finished day are only kept as a combined figure, shown under Crack Use.
            rows.add(new StockRowResponse(r.category(), r.opening(), r.production(), r.totalSales(), r.crackUse(), 0,
                    r.closing(), price.get(r.category()).longValue(), lockService.isLocked(Mod.WHOLE_EGG, r.category().wire())));
        }
        return rows;
    }

    private double closingFor(CatKey category) {
        return stockRows().stream().filter(r -> r.category() == category).findFirst().map(StockRowResponse::closing).orElse(0.0);
    }

    // ── Customers ─────────────────────────────────────────────────────────

    @Audited(module = Mod.WHOLE_EGG, action = "Add Customer", type = LogType.AUDIT,
            detail = "'New customer: ' + #request.firstName() + ' ' + #request.lastName()")
    @Transactional
    public Customer createCustomer(CreateCustomerRequest request, String username) {
        if (request.openingCredit() > 0 && request.openingAdvance() > 0) {
            throw new BadRequestException("A customer can't both owe money and have an advance at the same time.");
        }
        Customer customer = Customer.builder()
                .firstName(request.firstName()).lastName(request.lastName()).phone(request.phone())
                .state(request.state()).lga(request.lga()).street(request.street())
                .createdBy(username)
                .build();
        customer = customerRepository.save(customer);

        if (request.openingCredit() > 0 || request.openingAdvance() > 0) {
            WeSaleTransaction opening = WeSaleTransaction.builder()
                    .customer(customer).customerNameSnapshot(customer.fullName()).state(customer.getState())
                    .type(WeSaleTxnType.OPENING)
                    .credit(request.openingCredit()).advance(request.openingAdvance())
                    .enteredBy(username)
                    .build();
            transactionRepository.save(opening);
        }
        return customer;
    }

    /**
     * Lets staff go back to an existing customer profile and record (or fix, or
     * clear) what the customer already owed / had in advance before using the
     * system. Only allowed while the customer has no real sale or payment yet:
     * once they have, every later transaction's running balance was built on
     * top of the old opening figure, so changing it would silently shift all
     * of them.
     */
    @Audited(module = Mod.WHOLE_EGG, action = "Set Opening Balance", type = LogType.AUDIT,
            detail = "'Customer #' + #customerId + ' -> owes ' + #request.openingCredit() + ' / advance ' + #request.openingAdvance()")
    @Transactional
    public Customer setOpeningBalance(Long customerId, SetOpeningBalanceRequest request, String username) {
        if (request.openingCredit() > 0 && request.openingAdvance() > 0) {
            throw new BadRequestException("A customer can't both owe money and have an advance at the same time.");
        }
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new NotFoundException("Customer not found."));
        if (transactionRepository.existsByCustomerAndTypeIn(customer, List.of(WeSaleTxnType.SALE, WeSaleTxnType.PAYMENT))) {
            throw new BadRequestException("This customer already has sales or payments, so the opening balance can no longer be changed.");
        }
        // One free edit per customer. After it the field is locked; a further edit needs an
        // Administrator-approved Opening Stock request for this customer's scope.
        String scope = openingBalanceScope(customerId);
        if (lockService.isLocked(Mod.WHOLE_EGG, scope)) {
            throw new ForbiddenException("This customer's opening balance has already been set once. Request the Administrator's approval to edit it again.");
        }
        var existing = transactionRepository.findFirstByCustomerAndType(customer, WeSaleTxnType.OPENING);
        boolean clear = request.openingCredit() == 0 && request.openingAdvance() == 0;
        if (clear) {
            existing.ifPresent(transactionRepository::delete);
        } else if (existing.isPresent()) {
            WeSaleTransaction opening = existing.get();
            opening.setCredit(request.openingCredit());
            opening.setAdvance(request.openingAdvance());
            opening.setUpdatedBy(username);
            transactionRepository.save(opening);
        } else {
            transactionRepository.save(WeSaleTransaction.builder()
                    .customer(customer).customerNameSnapshot(customer.fullName()).state(customer.getState())
                    .type(WeSaleTxnType.OPENING)
                    .credit(request.openingCredit()).advance(request.openingAdvance())
                    .enteredBy(username)
                    .build());
        }
        lockService.lock(Mod.WHOLE_EGG, scope);
        return customer;
    }

    /** Opening Stock lock scope for one customer's opening balance (shared lock/request machinery). */
    public static String openingBalanceScope(Long customerId) {
        return "customer-opening-" + customerId;
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponse> listCustomers(Pageable pageable, boolean isAdmin) {
        return customerRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(c -> toCustomerResponse(c, isAdmin));
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> searchCustomers(String query, boolean isAdmin) {
        return customerRepository.search(query, PageRequest.of(0, 20)).stream()
                .map(c -> toCustomerResponse(c, isAdmin))
                .toList();
    }

    /** Most recently active customers first, then most-recently-created customers with no sale yet — matches WholeEggView's recentCustomers picker shortlist. */
    @Transactional(readOnly = true)
    public List<CustomerResponse> recentCustomers(int limit, boolean isAdmin) {
        List<WeSaleTransactionRepository.CustomerActivityProjection> ranking =
                transactionRepository.customerActivityRanking(PageRequest.of(0, limit));

        LinkedHashMap<Long, Customer> ordered = new LinkedHashMap<>();
        for (var row : ranking) {
            customerRepository.findById(row.getCustomerId()).ifPresent(c -> ordered.put(c.getId(), c));
        }
        if (ordered.size() < limit) {
            for (Customer c : customerRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit))) {
                if (ordered.size() >= limit) break;
                ordered.putIfAbsent(c.getId(), c);
            }
        }
        return ordered.values().stream().map(c -> toCustomerResponse(c, isAdmin)).toList();
    }

    /** Positive = customer owes; negative = customer has an advance. Derived from the customer's single most recent transaction (a running balance, never summed). */
    @Transactional(readOnly = true)
    public double customerBalance(Customer customer) {
        return transactionRepository.findFirstByCustomerOrderByOccurredAtDesc(customer)
                .map(t -> t.getCredit() - t.getAdvance())
                .orElse(0.0);
    }

    /**
     * Builds one Customers-directory row, including the visitCount/
     * totalCrates/totalRevenue/lastPurchaseAt stats CustomerResponse gained
     * for the Phase 5 frontend swap. isAdmin=false restricts every one of
     * these (not just balance, which is always all-time) to the current
     * year — same rule as customerHistory/allTransactions. One extra handful
     * of bounded queries per customer, same N+1-per-page shape the existing
     * customerBalance() call already had before this was added — acceptable
     * at directory page sizes (20 rows), not worth a batch-query rewrite yet.
     */
    @Transactional(readOnly = true)
    public CustomerResponse toCustomerResponse(Customer customer, boolean isAdmin) {
        double balance = customerBalance(customer);
        Integer year = isAdmin ? null : LocalDate.now().getYear();
        long visits = isAdmin
                ? transactionRepository.countByCustomer(customer)
                : transactionRepository.countByCustomerAndTxnYear(customer, year);
        double crates = lineItemRepository.sumQtyForCustomer(customer, WeSaleTxnType.SALE, year);
        double revenue = lineItemRepository.sumRevenueForCustomer(customer, WeSaleTxnType.SALE, year);
        Instant lastPurchase = (isAdmin
                ? transactionRepository.findFirstByCustomerOrderByOccurredAtDesc(customer)
                : transactionRepository.findFirstByCustomerAndTxnYearOrderByOccurredAtDesc(customer, year))
                .map(WeSaleTransaction::getOccurredAt).orElse(null);
        return CustomerResponse.from(customer, balance, visits, crates, revenue, lastPurchase);
    }

    private double priorBalanceFor(WeSaleTransaction txn) {
        return transactionRepository.findFirstByCustomerAndOccurredAtLessThanOrderByOccurredAtDesc(txn.getCustomer(), txn.getOccurredAt())
                .map(t -> t.getCredit() - t.getAdvance())
                .orElse(0.0);
    }

    // ── Back-dated entries ────────────────────────────────────────────────

    /** The day an entry is for: null means today; future is refused; a past day must be inside the grace window or approved. */
    private LocalDate resolveEntryDay(LocalDate requested) {
        LocalDate today = LocalDate.now();
        LocalDate day = requested == null ? today : requested;
        if (day.isAfter(today)) {
            throw new BadRequestException("You can't enter records for a future date.");
        }
        if (!day.isEqual(today) && !editWindow.isOpen(Mod.WHOLE_EGG, day)) {
            throw new ForbiddenException("This day isn't open for entry. Days must be entered in order - the banner at the top shows the day to work on. A day that is already closed needs the Administrator's permission.");
        }
        return day;
    }

    /** Midday on a past day, so it sorts inside that day's reports; null for today (the entity stamps "now"). */
    private Instant occurredAtFor(LocalDate day) {
        return day.isEqual(LocalDate.now()) ? null : day.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant();
    }

    /**
     * Each transaction stores the customer's running balance after it. When an
     * entry is inserted or changed in the past, every later transaction of that
     * customer has to be re-walked so the balances stay correct.
     */
    private void recomputeLedger(Customer customer) {
        double running = 0;
        for (WeSaleTransaction t : transactionRepository.findAllByCustomerOrderByOccurredAtAsc(customer)) {
            if (t.getType() == WeSaleTxnType.OPENING) {
                running = t.getCredit() - t.getAdvance();
                continue;
            }
            double due = lineItemRepository.findAllByTransaction(t).stream().mapToDouble(i -> i.getQty() * i.getPrice()).sum();
            double remaining = due + running - t.getAmountPaid();
            t.setCredit(Math.max(remaining, 0));
            t.setAdvance(Math.max(-remaining, 0));
            transactionRepository.save(t);
            running = remaining;
        }
    }

    /** Adds crack use / gifts that belong to a day that has already closed; later days' stock follows. */
    @Audited(module = Mod.WHOLE_EGG, action = "Record Past-day Crack/Gift", type = LogType.AUDIT,
            detail = "#request.kind() + ' for ' + #request.date()")
    @Transactional
    public void recordPastAdjustment(PastAdjustmentRequest request) {
        LocalDate day = resolveEntryDay(request.date());
        if (day.isEqual(LocalDate.now())) {
            throw new BadRequestException("For today, use the normal Commit button.");
        }
        double crackTotal = 0;
        for (PastQtyEntry e : request.values()) {
            // A negative quantity takes away something entered by mistake (e.g. a crack figure saved twice).
            if (e.qty() == 0) continue;
            productionService.carryDeltaForward(day, e.category(), -e.qty());
            crackTotal += e.qty();
        }
        if (request.kind() == PastAdjustmentRequest.Kind.CRACK && crackTotal != 0) {
            productionService.addCrackSnapshot(day, crackTotal);
        }
    }

    // ── Sales / Payments ──────────────────────────────────────────────────

    @Audited(module = Mod.WHOLE_EGG, action = "Record Sale", type = LogType.AUDIT,
            detail = "'Customer #' + #request.customerId()")
    @Transactional
    public WeSaleTransaction createSale(CreateSaleRequest request, String username) {
        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new NotFoundException("Customer not found."));

        LocalDate day = resolveEntryDay(request.date());
        boolean past = !day.isEqual(LocalDate.now());
        Instant when = occurredAtFor(day);
        double totalQty = request.items().stream().mapToDouble(SaleLineItemRequest::qty).sum();
        if (totalQty <= 0) {
            throw new BadRequestException("Enter at least one category quantity.");
        }
        Payment pay = resolvePayment(request.paymentMethods(), request.bank(), request.transfers(), request.cashAmount(), request.transferAmount());

        for (SaleLineItemRequest item : request.items()) {
            if (item.qty() <= 0) continue;
            double available = closingFor(item.category());
            if (item.qty() > available) {
                throw new BadRequestException("Cannot sell " + item.qty() + " crates of " + item.category().label() + " — only " + available + " available.");
            }
        }

        // Sales Amount = Quantity Sold × Price per Crate — must stay fractional (e.g. 0.5 crate × ₦4,000 = ₦2,000), never truncated to a long.
        double totalDue = request.items().stream().mapToDouble(i -> i.qty() * i.price()).sum();
        double priorBalance = past
                ? transactionRepository.findFirstByCustomerAndOccurredAtLessThanOrderByOccurredAtDesc(customer, when).map(t -> t.getCredit() - t.getAdvance()).orElse(0.0)
                : customerBalance(customer);
        long amountPaid = pay.cash() + pay.transferTotal();
        double remaining = totalDue + priorBalance - amountPaid;

        WeSaleTransaction txn = WeSaleTransaction.builder()
                .customer(customer).customerNameSnapshot(customer.fullName()).state(customer.getState())
                .occurredAt(when)
                .type(WeSaleTxnType.SALE)
                .paymentMethods(new HashSet<>(request.paymentMethods()))
                .bank(pay.bankLabel()).transfers(new ArrayList<>(pay.transfers()))
                .cashAmount(pay.cash()).transferAmount(pay.transferTotal()).amountPaid(amountPaid)
                .credit(Math.max(remaining, 0)).advance(Math.max(-remaining, 0))
                .enteredBy(username)
                .build();
        txn = transactionRepository.save(txn);

        for (SaleLineItemRequest item : request.items()) {
            if (item.qty() <= 0) continue;
            lineItemRepository.save(WeSaleLineItem.builder().transaction(txn).category(item.category()).qty(item.qty()).price(item.price()).build());
        }
        if (past) {
            recomputeLedger(customer);
            for (SaleLineItemRequest item : request.items()) {
                if (item.qty() > 0) {
                    productionService.carryDeltaForward(day, item.category(), -item.qty());
                }
            }
        }
        return txn;
    }

    @Audited(module = Mod.WHOLE_EGG, action = "Record Payment", type = LogType.AUDIT, detail = "'Customer #' + #request.customerId()")
    @Transactional
    public WeSaleTransaction recordPayment(RecordPaymentRequest request, String username) {
        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new NotFoundException("Customer not found."));

        LocalDate day = resolveEntryDay(request.date());
        boolean past = !day.isEqual(LocalDate.now());
        Instant when = occurredAtFor(day);
        Payment pay = resolvePayment(request.paymentMethods(), request.bank(), request.transfers(), request.cashAmount(), request.transferAmount());
        double balanceOwed = past
                ? transactionRepository.findFirstByCustomerAndOccurredAtLessThanOrderByOccurredAtDesc(customer, when).map(t -> t.getCredit() - t.getAdvance()).orElse(0.0)
                : customerBalance(customer);
        long amountPaid = pay.cash() + pay.transferTotal();
        if (amountPaid <= 0) {
            throw new BadRequestException("Enter the amount being paid.");
        }
        double remaining = balanceOwed - amountPaid;

        WeSaleTransaction txn = WeSaleTransaction.builder()
                .customer(customer).customerNameSnapshot(customer.fullName()).state(customer.getState())
                .occurredAt(when)
                .type(WeSaleTxnType.PAYMENT)
                .paymentMethods(new HashSet<>(request.paymentMethods()))
                .bank(pay.bankLabel()).transfers(new ArrayList<>(pay.transfers()))
                .cashAmount(pay.cash()).transferAmount(pay.transferTotal()).amountPaid(amountPaid)
                .credit(Math.max(remaining, 0)).advance(Math.max(-remaining, 0))
                .enteredBy(username)
                .build();
        txn = transactionRepository.save(txn);
        if (past) {
            recomputeLedger(customer);
        }
        return txn;
    }

    @Audited(module = Mod.WHOLE_EGG, action = "Edit Transaction", type = LogType.AUDIT, detail = "'Transaction #' + #id")
    @Transactional
    public WeSaleTransaction updateTransaction(Long id, UpdateSaleTxnRequest request, String username) {
        WeSaleTransaction txn = transactionRepository.findById(id).orElseThrow(() -> new NotFoundException("Transaction not found."));
        if (!isEditable(txn)) {
            throw new ForbiddenException("This day isn't open for entry. Days must be entered in order - the banner at the top shows the day to work on. A day that is already closed needs the Administrator's permission.");
        }
        Payment pay = resolvePayment(request.paymentMethods(), request.bank(), request.transfers(), request.cashAmount(), request.transferAmount());

        List<SaleLineItemRequest> items = request.items() != null ? request.items() : List.of();
        double totalQty = items.stream().mapToDouble(SaleLineItemRequest::qty).sum();
        if (txn.getType() == WeSaleTxnType.SALE && totalQty <= 0) {
            throw new BadRequestException("Enter at least one category quantity.");
        }
        double totalDue = items.stream().mapToDouble(i -> i.qty() * i.price()).sum();
        double priorBalance = priorBalanceFor(txn);
        long amountPaid = pay.cash() + pay.transferTotal();
        double remaining = totalDue + priorBalance - amountPaid;

        java.util.Map<CatKey, Double> oldQty = new java.util.EnumMap<>(CatKey.class);
        for (WeSaleLineItem old : lineItemRepository.findAllByTransaction(txn)) {
            oldQty.merge(old.getCategory(), old.getQty(), Double::sum);
        }
        lineItemRepository.deleteAllByTransaction(txn);
        for (SaleLineItemRequest item : items) {
            if (item.qty() <= 0) continue;
            lineItemRepository.save(WeSaleLineItem.builder().transaction(txn).category(item.category()).qty(item.qty()).price(item.price()).build());
        }

        txn.setPaymentMethods(new HashSet<>(request.paymentMethods()));
        txn.setBank(pay.bankLabel());
        txn.getTransfers().clear();
        txn.getTransfers().addAll(pay.transfers());
        txn.setCashAmount(pay.cash());
        txn.setTransferAmount(pay.transferTotal());
        txn.setAmountPaid(amountPaid);
        txn.setCredit(Math.max(remaining, 0));
        txn.setAdvance(Math.max(-remaining, 0));
        txn.setUpdatedBy(username);
        txn = transactionRepository.save(txn);
        LocalDate txnDay = txn.getOccurredAt().atZone(ZONE).toLocalDate();
        if (txnDay.isBefore(LocalDate.now())) {
            recomputeLedger(txn.getCustomer());
            if (txn.getType() == WeSaleTxnType.SALE) {
                java.util.Map<CatKey, Double> newQty = new java.util.EnumMap<>(CatKey.class);
                for (SaleLineItemRequest item : items) {
                    if (item.qty() > 0) newQty.merge(item.category(), item.qty(), Double::sum);
                }
                for (CatKey k : CatKey.values()) {
                    double delta = newQty.getOrDefault(k, 0.0) - oldQty.getOrDefault(k, 0.0);
                    productionService.carryDeltaForward(txnDay, k, -delta);
                }
            }
        }
        return txn;
    }

    @org.springframework.beans.factory.annotation.Value("${app.transactions.delete-enabled:false}")
    private boolean transactionDeleteEnabled;

    public boolean isTransactionDeleteEnabled() {
        return transactionDeleteEnabled;
    }

    /**
     * Administrator-only removal of a mistaken sale or debt payment. A sale's deduction from stock is simply
     * its line items, so removing them restores the stock; for an earlier day that restored stock is also pushed
     * into every later day's opening (and the live opening), and the customer's running balances are re-walked.
     */
    @Audited(module = Mod.WHOLE_EGG, action = "Delete Transaction", type = LogType.AUDIT, detail = "'Transaction #' + #id")
    @Transactional
    public void deleteTransaction(Long id) {
        if (!transactionDeleteEnabled) {
            throw new ForbiddenException("Deleting transactions is switched off.");
        }
        WeSaleTransaction txn = transactionRepository.findById(id).orElseThrow(() -> new NotFoundException("Transaction not found."));
        if (txn.getType() == WeSaleTxnType.OPENING) {
            throw new BadRequestException("An opening balance can't be deleted here.");
        }
        Customer customer = txn.getCustomer();
        LocalDate day = txn.getOccurredAt().atZone(ZONE).toLocalDate();
        java.util.Map<CatKey, Double> qty = new java.util.EnumMap<>(CatKey.class);
        if (txn.getType() == WeSaleTxnType.SALE) {
            for (WeSaleLineItem li : lineItemRepository.findAllByTransaction(txn)) {
                qty.merge(li.getCategory(), li.getQty(), Double::sum);
            }
        }
        lineItemRepository.deleteAllByTransaction(txn);
        transactionRepository.delete(txn);
        transactionRepository.flush();
        recomputeLedger(customer);
        if (day.isBefore(LocalDate.now())) {
            for (java.util.Map.Entry<CatKey, Double> e : qty.entrySet()) {
                productionService.carryDeltaForward(day, e.getKey(), e.getValue());
            }
        }
    }

    /**
     * Administrator-only (switched by TRANSACTION_DELETE_ENABLED): moves a customer's Opening Balance to the day it
     * really applied from (e.g. it was typed in days after go-live). It is placed at the very start of that day so it
     * comes before that day's sales, and the customer's running balances are re-walked so overpayments and credit net
     * correctly.
     */
    @Audited(module = Mod.WHOLE_EGG, action = "Move Opening Balance Date", type = LogType.AUDIT, detail = "'Transaction #' + #id + ' -> ' + #date")
    @Transactional
    public void moveOpeningBalanceDate(Long id, LocalDate date) {
        if (!transactionDeleteEnabled) {
            throw new ForbiddenException("Changing transactions is switched off.");
        }
        WeSaleTransaction txn = transactionRepository.findById(id).orElseThrow(() -> new NotFoundException("Transaction not found."));
        if (txn.getType() != WeSaleTxnType.OPENING) {
            throw new BadRequestException("Only an opening balance can be moved here.");
        }
        if (date == null || date.isAfter(LocalDate.now())) {
            throw new BadRequestException("Choose a day that is not in the future.");
        }
        Instant when = date.atStartOfDay(ZoneId.systemDefault()).plusMinutes(1).toInstant();
        txn.setOccurredAt(when);
        txn.setTxnYear(date.getYear());
        transactionRepository.saveAndFlush(txn);
        recomputeLedger(txn.getCustomer());
    }

    /**
     * Administrator-only (switched by TRANSACTION_DELETE_ENABLED): wipes the crack use and gifts recorded for a finished
     * day, e.g. after the same figure was saved several times. Stock is put back and later days' openings rise by the same
     * amounts; the day's Total Crack Use goes back to zero.
     */
    @Audited(module = Mod.WHOLE_EGG, action = "Clear Past-day Crack/Gift", type = LogType.AUDIT, detail = "'For ' + #day")
    @Transactional
    public void clearPastDayCrack(LocalDate day) {
        if (!transactionDeleteEnabled) {
            throw new ForbiddenException("Deleting transactions is switched off.");
        }
        if (!day.isBefore(LocalDate.now())) {
            throw new BadRequestException("Choose a day before today.");
        }
        for (com.kingsfarm.kingsfarmbackend.production.dto.CategoryStockRow r : productionService.categoryRowsFor(day)) {
            if (r.crackUse() > 0) {
                productionService.carryDeltaForward(day, r.category(), r.crackUse());
            }
        }
        productionService.clearCrackSnapshot(day);
    }

    /** Everything derived from the payment part of a request, already validated. */
    private record Payment(long cash, long transferTotal, List<BankTransfer> transfers, String bankLabel) {
    }

    /**
     * Validates the payment methods and works out what was actually paid.
     * Cash counts only if Cash is selected; transfers count only if Transfer
     * is selected. Multi-bank: when {@code transfers} is given, each row needs
     * a bank and an amount above zero, the same bank may repeat (separate transfers), and the
     * transfer total is the sum of the rows. If it's absent (older clients), the
     * single {@code bank} + {@code transferAmount} are treated as one row.
     */
    private Payment resolvePayment(List<PaymentMethod> methods, String bank, List<BankTransferRequest> transfers, long cashAmount, long transferAmount) {
        if (methods == null || methods.isEmpty()) {
            throw new BadRequestException("Select at least one payment method.");
        }
        long cash = methods.contains(PaymentMethod.CASH) ? cashAmount : 0;
        if (!methods.contains(PaymentMethod.TRANSFER)) {
            return new Payment(cash, 0, List.of(), null);
        }

        List<BankTransfer> rows = new ArrayList<>();
        if (transfers != null && !transfers.isEmpty()) {
            for (BankTransferRequest t : transfers) {
                if (t.bank() == null || t.bank().isBlank()) {
                    throw new BadRequestException("Please select a bank for every transfer.");
                }
                if (t.amount() <= 0) {
                    throw new BadRequestException("Enter the amount transferred to " + t.bank().trim() + ".");
                }
                rows.add(new BankTransfer(t.bank().trim(), t.amount()));
            }
        } else {
            if (bank == null || bank.isBlank()) {
                throw new BadRequestException("Please select the bank the customer transferred to.");
            }
            rows.add(new BankTransfer(bank.trim(), transferAmount));
        }
        long total = rows.stream().mapToLong(BankTransfer::getAmount).sum();
        String label = String.join(", ", rows.stream().map(BankTransfer::getBank).distinct().toList());
        if (label.length() > 64) {
            label = label.substring(0, 61) + "...";
        }
        return new Payment(cash, total, rows, label);
    }

    public boolean isEditable(WeSaleTransaction txn) {
        return editWindow.isOpen(Mod.WHOLE_EGG, txn.getOccurredAt().atZone(ZONE).toLocalDate());
    }

    // ── Responses / history ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public WeSaleTransactionResponse toResponse(WeSaleTransaction txn) {
        List<SaleLineItemResponse> items = lineItemRepository.findAllByTransaction(txn).stream()
                .map(li -> new SaleLineItemResponse(li.getCategory(), li.getQty(), li.getPrice(), li.getQty() * li.getPrice()))
                .toList();
        double revenue = items.stream().mapToDouble(SaleLineItemResponse::revenue).sum();
        return new WeSaleTransactionResponse(
                txn.getId(), txn.getCustomer().getId(), txn.getCustomerNameSnapshot(), txn.getState(),
                txn.getType(), txn.getOccurredAt(), txn.getTxnYear(), items, revenue,
                txn.getPaymentMethods(), txn.getBank(), txn.getCashAmount(), txn.getTransferAmount(), txn.getAmountPaid(),
                txn.getCredit(), txn.getAdvance(), txn.getEnteredBy(), txn.getUpdatedBy(), isEditable(txn), priorBalanceFor(txn),
                txn.getVerificationStatus(), txn.getVerificationRemark(), txn.getVerifiedBy(), txn.getVerifiedAt(),
                transferRows(txn)
        );
    }

    /** Per-bank rows for the response; rows saved before multi-bank support fall back to their single bank + transfer amount. */
    private List<BankTransferResponse> transferRows(WeSaleTransaction txn) {
        if (!txn.getTransfers().isEmpty()) {
            return txn.getTransfers().stream().map(t -> new BankTransferResponse(t.getBank(), t.getAmount())).toList();
        }
        if (txn.getBank() != null && !txn.getBank().isBlank() && txn.getTransferAmount() > 0) {
            return List.of(new BankTransferResponse(txn.getBank(), txn.getTransferAmount()));
        }
        return List.of();
    }

    /** isAdmin sees every year; a manager is restricted to the current year — matches WholeEggView's customerVisibleTxns. */
    @Transactional(readOnly = true)
    public Page<WeSaleTransaction> customerHistory(Long customerId, boolean isAdmin, Pageable pageable) {
        Customer customer = customerRepository.findById(customerId).orElseThrow(() -> new NotFoundException("Customer not found."));
        if (isAdmin) {
            return transactionRepository.findAllByCustomerOrderByOccurredAtDesc(customer, pageable);
        }
        return transactionRepository.findAllByCustomerAndTxnYearOrderByOccurredAtDesc(customer, LocalDate.now().getYear(), pageable);
    }

    @Transactional(readOnly = true)
    public Page<WeSaleTransaction> allTransactions(boolean isAdmin, String bank, LocalDate date, Pageable pageable) {
        int minYear = isAdmin ? 0 : LocalDate.now().getYear();
        Instant start = date == null ? Instant.EPOCH : com.kingsfarm.kingsfarmbackend.common.AppDay.start(date);
        Instant end = date == null ? Instant.parse("9999-01-01T00:00:00Z") : com.kingsfarm.kingsfarmbackend.common.AppDay.end(date);
        String b = bank == null ? "" : bank.trim();
        if (b.equalsIgnoreCase("cash")) {
            return transactionRepository.findFilteredByMethod(minYear, PaymentMethod.CASH, start, end, pageable);
        }
        return transactionRepository.findFiltered(minYear, b, start, end, pageable);
    }

    // ── Payment Auditing (Administrator-only, Whole Egg only) ──────────────

    /**
     * Transfer-only transactions in a half-open [start, end) instant range,
     * most recent first — backs the Payment Auditing tab. Cash-only
     * transactions never appear here, matching the feature's whole premise:
     * cash is already confirmed at time of sale, only transfers need
     * checking against the bank. Period navigation (Today/Week/Month/
     * Quarter/Half-Year) is resolved to a concrete [start, end) range by the
     * controller before it gets here, same idiom as the daily/monthly
     * report endpoints.
     */
    @Transactional(readOnly = true)
    public Page<WeSaleTransaction> transferTransactions(Instant start, Instant end, Pageable pageable) {
        return transactionRepository.findAllByPaymentMethodAndOccurredAtRange(PaymentMethod.TRANSFER, start, end, pageable);
    }

    /**
     * The admin's tick/cross + remark for one transfer payment. Guards
     * against verifying a cash-only transaction even though the UI never
     * offers one — the whole point of this field is "did the transfer
     * land", which is meaningless for a payment that was never a transfer.
     */
    @Audited(module = Mod.WHOLE_EGG, action = "Verify Transfer Payment", type = LogType.AUDIT,
            detail = "'Txn #' + #id + ': ' + #request.status()")
    @Transactional
    public WeSaleTransaction verifyTransaction(Long id, VerifyPaymentRequest request, String adminUsername) {
        WeSaleTransaction txn = transactionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Transaction not found."));
        if (!txn.getPaymentMethods().contains(PaymentMethod.TRANSFER)) {
            throw new BadRequestException("Only bank-transfer payments can be verified.");
        }
        txn.setVerificationStatus(request.status());
        txn.setVerificationRemark(request.remark());
        txn.setVerifiedBy(adminUsername);
        txn.setVerifiedAt(Instant.now());
        return transactionRepository.save(txn);
    }

    /** Farm-wide, all-time, not year-restricted — a customer's outstanding balance doesn't reset each year just because a manager's browsable history does. */
    @Transactional(readOnly = true)
    public OutstandingBalanceResponse outstandingTotals() {
        WeSaleTransactionRepository.OutstandingTotals totals = transactionRepository.outstandingTotals();
        return new OutstandingBalanceResponse(totals.getTotalCredit(), totals.getTotalAdvance());
    }

    // ── Reports (BACKEND_PLAN.md §8) ────────────────────────────────────────

    private static final List<ReportColumn> REPORT_COLUMNS = List.of(
            new ReportColumn("txns", "Transactions", true),
            new ReportColumn("crates", "Crates Sold", true),
            new ReportColumn("revenue", "Revenue", false),
            new ReportColumn("avg", "Avg/Txn", false),
            new ReportColumn("opening", "Opening Stock (crates)", true),
            new ReportColumn("closing", "Closing Stock (crates)", true)
    );

    @Transactional(readOnly = true)
    public ReportTableResponse dailyReport(LocalDate start, LocalDate end) {
        Instant rangeStart = ReportPeriods.startOfDay(start);
        Instant rangeEnd = ReportPeriods.startOfNextDay(end);
        Map<LocalDate, List<WeSaleTransaction>> txnsByDate = salesInRange(rangeStart, rangeEnd).stream()
                .collect(Collectors.groupingBy(t -> t.getOccurredAt().atZone(ZONE).toLocalDate()));
        Map<LocalDate, List<WeSaleLineItemRepository.SaleLineProjection>> linesByDate = lineItemsInRange(rangeStart, rangeEnd).stream()
                .collect(Collectors.groupingBy(li -> li.getOccurredAt().atZone(ZONE).toLocalDate()));
        Map<LocalDate, double[]> stockByDay = productionService.eggStockByDay(start, end);
        double[] noStock = new double[]{0, 0};
        List<Map<String, Object>> rows = ReportPeriods.daysBetween(start, end).stream()
                .map(date -> {
                    Map<String, Object> row = reportRow(ReportPeriods.dayLabel(date), txnsByDate.getOrDefault(date, List.of()), linesByDate.getOrDefault(date, List.of()));
                    double[] st = stockByDay.getOrDefault(date, noStock);
                    row.put("opening", st[0]);
                    row.put("closing", st[1]);
                    return row;
                })
                .toList();
        return new ReportTableResponse(REPORT_COLUMNS, rows);
    }

    @Transactional(readOnly = true)
    public ReportTableResponse monthlyReport(int months) {
        List<YearMonth> monthsList = ReportPeriods.trailingMonths(months);
        Instant rangeStart = ReportPeriods.startOfDay(monthsList.get(0).atDay(1));
        Instant rangeEnd = ReportPeriods.startOfNextDay(monthsList.get(monthsList.size() - 1).atEndOfMonth());
        Map<YearMonth, List<WeSaleTransaction>> txnsByMonth = salesInRange(rangeStart, rangeEnd).stream()
                .collect(Collectors.groupingBy(t -> YearMonth.from(t.getOccurredAt().atZone(ZONE).toLocalDate())));
        Map<YearMonth, List<WeSaleLineItemRepository.SaleLineProjection>> linesByMonth = lineItemsInRange(rangeStart, rangeEnd).stream()
                .collect(Collectors.groupingBy(li -> YearMonth.from(li.getOccurredAt().atZone(ZONE).toLocalDate())));
        Map<LocalDate, double[]> stockByDay = productionService.eggStockByDay(monthsList.get(0).atDay(1), monthsList.get(monthsList.size() - 1).atEndOfMonth());
        List<Map<String, Object>> rows = monthsList.stream()
                .map(month -> {
                    Map<String, Object> row = reportRow(ReportPeriods.monthLabel(month), txnsByMonth.getOrDefault(month, List.of()), linesByMonth.getOrDefault(month, List.of()));
                    double[] st = ProductionService.monthEnds(stockByDay, month, 2, 0);
                    row.put("opening", st[0]);
                    row.put("closing", st[1]);
                    return row;
                })
                .toList();
        return new ReportTableResponse(REPORT_COLUMNS, rows);
    }

    private List<WeSaleTransaction> salesInRange(Instant start, Instant end) {
        return transactionRepository.findAllByTypeAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(WeSaleTxnType.SALE, start, end);
    }

    private List<WeSaleLineItemRepository.SaleLineProjection> lineItemsInRange(Instant start, Instant end) {
        return lineItemRepository.lineItemsInRange(WeSaleTxnType.SALE, start, end);
    }

    private Map<String, Object> reportRow(String periodLabel, List<WeSaleTransaction> txns, List<WeSaleLineItemRepository.SaleLineProjection> lines) {
        int txnCount = txns.size();
        double crates = lines.stream().mapToDouble(WeSaleLineItemRepository.SaleLineProjection::getQty).sum();
        double revenue = lines.stream().mapToDouble(li -> li.getQty() * li.getPrice()).sum();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("period", periodLabel);
        row.put("txns", txnCount);
        row.put("crates", crates);
        row.put("revenue", revenue);
        row.put("avg", txnCount == 0 ? 0 : revenue / txnCount);
        return row;
    }
}
