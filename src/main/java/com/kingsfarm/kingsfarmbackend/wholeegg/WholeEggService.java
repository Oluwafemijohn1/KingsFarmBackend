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

    private long categoryValue(WeCategoryValueKind kind, CatKey category) {
        return categoryValueRepository.findByKindAndCategory(kind, category).map(WeCategoryValue::getValue).orElse(0L);
    }

    private Map<CatKey, Long> categoryMap(WeCategoryValueKind kind) {
        Map<CatKey, Long> map = new EnumMap<>(CatKey.class);
        for (CatKey k : CatKey.values()) {
            map.put(k, 0L);
        }
        for (WeCategoryValue v : categoryValueRepository.findAllByKind(kind)) {
            map.put(v.getCategory(), v.getValue());
        }
        return map;
    }

    private void setCategoryValue(WeCategoryValueKind kind, CatKey category, long value) {
        WeCategoryValue entity = categoryValueRepository.findByKindAndCategory(kind, category)
                .orElseGet(() -> WeCategoryValue.builder().kind(kind).category(category).build());
        entity.setValue(value);
        categoryValueRepository.save(entity);
    }

    /** Public "public-facing crack use" reader for ProductionService's Whole Egg → Production feed (see wiring task). */
    @Transactional(readOnly = true)
    public int salesCrackFor(CatKey category) {
        return (int) categoryValue(WeCategoryValueKind.SALES_CRACK, category);
    }

    @Transactional(readOnly = true)
    public int giftFor(CatKey category) {
        return (int) categoryValue(WeCategoryValueKind.GIFT, category);
    }

    /** Sum of every SALE-type transaction's qty for a category — Production's auto-received "Total Sales". */
    @Transactional(readOnly = true)
    public int totalSalesFor(CatKey category) {
        return soldQtyByCategory().getOrDefault(category, 0);
    }

    private Map<CatKey, Integer> soldQtyByCategory() {
        Map<CatKey, Integer> map = new EnumMap<>(CatKey.class);
        for (CatKey k : CatKey.values()) {
            map.put(k, 0);
        }
        for (WeSaleLineItemRepository.CategoryQtyProjection p : lineItemRepository.sumSoldQtyByCategory()) {
            map.put(p.getCategory(), (int) p.getQty());
        }
        return map;
    }

    @Audited(module = Mod.WHOLE_EGG, action = "Update Opening Stock", detail = "#request.category() + ' -> ' + #request.value()")
    @Transactional
    public void setOpening(UpdateCategoryValueRequest request) {
        if (lockService.isLocked(Mod.WHOLE_EGG, request.category().wire())) {
            throw new ForbiddenException("Opening Stock is locked. Submit an Opening Stock request for the Administrator to approve.");
        }
        setCategoryValue(WeCategoryValueKind.OPENING, request.category(), request.value());
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
        Map<CatKey, Long> opening = categoryMap(WeCategoryValueKind.OPENING);
        Map<CatKey, Long> price = categoryMap(WeCategoryValueKind.PRICE);
        Map<CatKey, Long> salesCrack = categoryMap(WeCategoryValueKind.SALES_CRACK);
        Map<CatKey, Long> gift = categoryMap(WeCategoryValueKind.GIFT);
        Map<CatKey, Integer> production = productionService.catProdTotals(LocalDate.now());
        Map<CatKey, Integer> sales = soldQtyByCategory();

        List<StockRowResponse> rows = new ArrayList<>();
        for (CatKey k : CatKey.values()) {
            int op = opening.get(k).intValue();
            int prod = production.getOrDefault(k, 0);
            int sold = sales.getOrDefault(k, 0);
            int crack = salesCrack.get(k).intValue();
            int g = gift.get(k).intValue();
            int closing = op + prod - sold - g - crack;
            rows.add(new StockRowResponse(k, op, prod, sold, crack, g, closing, price.get(k), lockService.isLocked(Mod.WHOLE_EGG, k.wire())));
        }
        return rows;
    }

    private int closingFor(CatKey category) {
        return stockRows().stream().filter(r -> r.category() == category).findFirst().map(StockRowResponse::closing).orElse(0);
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

    @Transactional(readOnly = true)
    public Page<CustomerResponse> listCustomers(Pageable pageable) {
        return customerRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(c -> CustomerResponse.from(c, customerBalance(c)));
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> searchCustomers(String query) {
        return customerRepository.search(query, PageRequest.of(0, 20)).stream()
                .map(c -> CustomerResponse.from(c, customerBalance(c)))
                .toList();
    }

    /** Most recently active customers first, then most-recently-created customers with no sale yet — matches WholeEggView's recentCustomers picker shortlist. */
    @Transactional(readOnly = true)
    public List<CustomerResponse> recentCustomers(int limit) {
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
        return ordered.values().stream().map(c -> CustomerResponse.from(c, customerBalance(c))).toList();
    }

    /** Positive = customer owes; negative = customer has an advance. Derived from the customer's single most recent transaction (a running balance, never summed). */
    @Transactional(readOnly = true)
    public long customerBalance(Customer customer) {
        return transactionRepository.findFirstByCustomerOrderByOccurredAtDesc(customer)
                .map(t -> t.getCredit() - t.getAdvance())
                .orElse(0L);
    }

    private long priorBalanceFor(WeSaleTransaction txn) {
        return transactionRepository.findFirstByCustomerAndOccurredAtLessThanOrderByOccurredAtDesc(txn.getCustomer(), txn.getOccurredAt())
                .map(t -> t.getCredit() - t.getAdvance())
                .orElse(0L);
    }

    // ── Sales / Payments ──────────────────────────────────────────────────

    @Audited(module = Mod.WHOLE_EGG, action = "Record Sale", type = LogType.AUDIT,
            detail = "'Customer #' + #request.customerId()")
    @Transactional
    public WeSaleTransaction createSale(CreateSaleRequest request, String username) {
        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new NotFoundException("Customer not found."));

        int totalQty = request.items().stream().mapToInt(SaleLineItemRequest::qty).sum();
        if (totalQty == 0) {
            throw new BadRequestException("Enter at least one category quantity.");
        }
        validatePaymentMethods(request.paymentMethods(), request.bank());

        for (SaleLineItemRequest item : request.items()) {
            if (item.qty() <= 0) continue;
            int available = closingFor(item.category());
            if (item.qty() > available) {
                throw new BadRequestException("Cannot sell " + item.qty() + " crates of " + item.category().label() + " — only " + available + " available.");
            }
        }

        long totalDue = request.items().stream().mapToLong(i -> (long) i.qty() * i.price()).sum();
        long priorBalance = customerBalance(customer);
        long[] amounts = effectiveAmounts(request.paymentMethods(), request.cashAmount(), request.transferAmount());
        long amountPaid = amounts[0] + amounts[1];
        long remaining = totalDue + priorBalance - amountPaid;

        WeSaleTransaction txn = WeSaleTransaction.builder()
                .customer(customer).customerNameSnapshot(customer.fullName()).state(customer.getState())
                .type(WeSaleTxnType.SALE)
                .paymentMethods(new HashSet<>(request.paymentMethods()))
                .bank(request.paymentMethods().contains(PaymentMethod.TRANSFER) ? request.bank() : null)
                .cashAmount(amounts[0]).transferAmount(amounts[1]).amountPaid(amountPaid)
                .credit(Math.max(remaining, 0)).advance(Math.max(-remaining, 0))
                .enteredBy(username)
                .build();
        txn = transactionRepository.save(txn);

        for (SaleLineItemRequest item : request.items()) {
            if (item.qty() <= 0) continue;
            lineItemRepository.save(WeSaleLineItem.builder().transaction(txn).category(item.category()).qty(item.qty()).price(item.price()).build());
        }
        return txn;
    }

    @Audited(module = Mod.WHOLE_EGG, action = "Record Payment", type = LogType.AUDIT, detail = "'Customer #' + #request.customerId()")
    @Transactional
    public WeSaleTransaction recordPayment(RecordPaymentRequest request, String username) {
        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new NotFoundException("Customer not found."));

        validatePaymentMethods(request.paymentMethods(), request.bank());
        long balanceOwed = customerBalance(customer);
        long[] amounts = effectiveAmounts(request.paymentMethods(), request.cashAmount(), request.transferAmount());
        long amountPaid = amounts[0] + amounts[1];
        if (amountPaid <= 0) {
            throw new BadRequestException("Enter the amount being paid.");
        }
        long remaining = balanceOwed - amountPaid;

        WeSaleTransaction txn = WeSaleTransaction.builder()
                .customer(customer).customerNameSnapshot(customer.fullName()).state(customer.getState())
                .type(WeSaleTxnType.PAYMENT)
                .paymentMethods(new HashSet<>(request.paymentMethods()))
                .bank(request.paymentMethods().contains(PaymentMethod.TRANSFER) ? request.bank() : null)
                .cashAmount(amounts[0]).transferAmount(amounts[1]).amountPaid(amountPaid)
                .credit(Math.max(remaining, 0)).advance(Math.max(-remaining, 0))
                .enteredBy(username)
                .build();
        return transactionRepository.save(txn);
    }

    @Audited(module = Mod.WHOLE_EGG, action = "Edit Transaction", type = LogType.AUDIT, detail = "'Transaction #' + #id")
    @Transactional
    public WeSaleTransaction updateTransaction(Long id, UpdateSaleTxnRequest request, String username) {
        WeSaleTransaction txn = transactionRepository.findById(id).orElseThrow(() -> new NotFoundException("Transaction not found."));
        if (!isEditable(txn)) {
            throw new ForbiddenException("This transaction was recorded on a previous day and can no longer be edited.");
        }
        validatePaymentMethods(request.paymentMethods(), request.bank());

        List<SaleLineItemRequest> items = request.items() != null ? request.items() : List.of();
        int totalQty = items.stream().mapToInt(SaleLineItemRequest::qty).sum();
        if (txn.getType() == WeSaleTxnType.SALE && totalQty == 0) {
            throw new BadRequestException("Enter at least one category quantity.");
        }
        long totalDue = items.stream().mapToLong(i -> (long) i.qty() * i.price()).sum();
        long priorBalance = priorBalanceFor(txn);
        long[] amounts = effectiveAmounts(request.paymentMethods(), request.cashAmount(), request.transferAmount());
        long amountPaid = amounts[0] + amounts[1];
        long remaining = totalDue + priorBalance - amountPaid;

        lineItemRepository.deleteAllByTransaction(txn);
        for (SaleLineItemRequest item : items) {
            if (item.qty() <= 0) continue;
            lineItemRepository.save(WeSaleLineItem.builder().transaction(txn).category(item.category()).qty(item.qty()).price(item.price()).build());
        }

        txn.setPaymentMethods(new HashSet<>(request.paymentMethods()));
        txn.setBank(request.paymentMethods().contains(PaymentMethod.TRANSFER) ? request.bank() : null);
        txn.setCashAmount(amounts[0]);
        txn.setTransferAmount(amounts[1]);
        txn.setAmountPaid(amountPaid);
        txn.setCredit(Math.max(remaining, 0));
        txn.setAdvance(Math.max(-remaining, 0));
        txn.setUpdatedBy(username);
        return transactionRepository.save(txn);
    }

    private void validatePaymentMethods(List<PaymentMethod> methods, String bank) {
        if (methods == null || methods.isEmpty()) {
            throw new BadRequestException("Select at least one payment method.");
        }
        if (methods.contains(PaymentMethod.TRANSFER) && (bank == null || bank.isBlank())) {
            throw new BadRequestException("Please select the bank the customer transferred to.");
        }
    }

    /** [cashAmount, transferAmount] — zeroed out for any method not actually selected, same as the frontend's "effective" amounts. */
    private long[] effectiveAmounts(List<PaymentMethod> methods, long cashAmount, long transferAmount) {
        long cash = methods.contains(PaymentMethod.CASH) ? cashAmount : 0;
        long transfer = methods.contains(PaymentMethod.TRANSFER) ? transferAmount : 0;
        return new long[]{cash, transfer};
    }

    public boolean isEditable(WeSaleTransaction txn) {
        return txn.getOccurredAt().atZone(ZONE).toLocalDate().isEqual(LocalDate.now());
    }

    // ── Responses / history ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public WeSaleTransactionResponse toResponse(WeSaleTransaction txn) {
        List<SaleLineItemResponse> items = lineItemRepository.findAllByTransaction(txn).stream()
                .map(li -> new SaleLineItemResponse(li.getCategory(), li.getQty(), li.getPrice(), (long) li.getQty() * li.getPrice()))
                .toList();
        long revenue = items.stream().mapToLong(SaleLineItemResponse::revenue).sum();
        return new WeSaleTransactionResponse(
                txn.getId(), txn.getCustomer().getId(), txn.getCustomerNameSnapshot(), txn.getState(),
                txn.getType(), txn.getOccurredAt(), txn.getTxnYear(), items, revenue,
                txn.getPaymentMethods(), txn.getBank(), txn.getCashAmount(), txn.getTransferAmount(), txn.getAmountPaid(),
                txn.getCredit(), txn.getAdvance(), txn.getEnteredBy(), txn.getUpdatedBy(), isEditable(txn), priorBalanceFor(txn)
        );
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
    public Page<WeSaleTransaction> allTransactions(boolean isAdmin, Pageable pageable) {
        if (isAdmin) {
            return transactionRepository.findAllByOrderByOccurredAtDesc(pageable);
        }
        return transactionRepository.findAllByTxnYearOrderByOccurredAtDesc(LocalDate.now().getYear(), pageable);
    }

    // ── Reports (BACKEND_PLAN.md §8) ────────────────────────────────────────

    private static final List<ReportColumn> REPORT_COLUMNS = List.of(
            new ReportColumn("txns", "Transactions", true),
            new ReportColumn("crates", "Crates Sold", true),
            new ReportColumn("revenue", "Revenue", false),
            new ReportColumn("avg", "Avg/Txn", false)
    );

    @Transactional(readOnly = true)
    public ReportTableResponse dailyReport(LocalDate start, LocalDate end) {
        Instant rangeStart = ReportPeriods.startOfDay(start);
        Instant rangeEnd = ReportPeriods.startOfNextDay(end);
        Map<LocalDate, List<WeSaleTransaction>> txnsByDate = salesInRange(rangeStart, rangeEnd).stream()
                .collect(Collectors.groupingBy(t -> t.getOccurredAt().atZone(ZONE).toLocalDate()));
        Map<LocalDate, List<WeSaleLineItemRepository.SaleLineProjection>> linesByDate = lineItemsInRange(rangeStart, rangeEnd).stream()
                .collect(Collectors.groupingBy(li -> li.getOccurredAt().atZone(ZONE).toLocalDate()));
        List<Map<String, Object>> rows = ReportPeriods.daysBetween(start, end).stream()
                .map(date -> reportRow(ReportPeriods.dayLabel(date), txnsByDate.getOrDefault(date, List.of()), linesByDate.getOrDefault(date, List.of())))
                .toList();
        return new ReportTableResponse(REPORT_COLUMNS, rows);
    }

    @Transactional(readOnly = true)
    public ReportTableResponse monthlyReport(int months) {
        List<YearMonth> monthsList = ReportPeriods.trailingMonths(months);
        Instant rangeStart = ReportPeriods.startOfDay(monthsList.getFirst().atDay(1));
        Instant rangeEnd = ReportPeriods.startOfNextDay(monthsList.getLast().atEndOfMonth());
        Map<YearMonth, List<WeSaleTransaction>> txnsByMonth = salesInRange(rangeStart, rangeEnd).stream()
                .collect(Collectors.groupingBy(t -> YearMonth.from(t.getOccurredAt().atZone(ZONE).toLocalDate())));
        Map<YearMonth, List<WeSaleLineItemRepository.SaleLineProjection>> linesByMonth = lineItemsInRange(rangeStart, rangeEnd).stream()
                .collect(Collectors.groupingBy(li -> YearMonth.from(li.getOccurredAt().atZone(ZONE).toLocalDate())));
        List<Map<String, Object>> rows = monthsList.stream()
                .map(month -> reportRow(ReportPeriods.monthLabel(month), txnsByMonth.getOrDefault(month, List.of()), linesByMonth.getOrDefault(month, List.of())))
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
        long crates = lines.stream().mapToLong(WeSaleLineItemRepository.SaleLineProjection::getQty).sum();
        long revenue = lines.stream().mapToLong(li -> (long) li.getQty() * li.getPrice()).sum();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("period", periodLabel);
        row.put("txns", txnCount);
        row.put("crates", crates);
        row.put("revenue", revenue);
        row.put("avg", txnCount == 0 ? 0 : Math.round((double) revenue / txnCount));
        return row;
    }
}
