package com.kingsfarm.kingsfarmbackend.crackegg;

import com.kingsfarm.kingsfarmbackend.audit.Audited;
import com.kingsfarm.kingsfarmbackend.common.Mod;
import com.kingsfarm.kingsfarmbackend.common.PaymentMethod;
import com.kingsfarm.kingsfarmbackend.common.exception.BadRequestException;
import com.kingsfarm.kingsfarmbackend.common.exception.ForbiddenException;
import com.kingsfarm.kingsfarmbackend.common.exception.NotFoundException;
import com.kingsfarm.kingsfarmbackend.common.reports.ReportColumn;
import com.kingsfarm.kingsfarmbackend.common.reports.ReportPeriods;
import com.kingsfarm.kingsfarmbackend.common.reports.ReportTableResponse;
import com.kingsfarm.kingsfarmbackend.crackegg.dto.*;
import com.kingsfarm.kingsfarmbackend.openingstock.OpeningStockLockService;
import com.kingsfarm.kingsfarmbackend.production.ProductionDayState;
import com.kingsfarm.kingsfarmbackend.production.ProductionService;
import com.kingsfarm.kingsfarmbackend.systemlog.LogType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Backs CrackEggView in full. Good Crack's Production/Received figures
 * (crackGoodProd/goodClassify) and Rough Crack's (crackRoughProd/
 * roughClassify) are read live from ProductionService's today's
 * ProductionDayState — the same cross-module read WholeEggService does for
 * catProdTotals. Good Crack's Gift qty and Sales total flow the other way
 * (Crack Egg → Production), so ProductionService injects this service
 * {@code @Lazy} for the same reason it does WholeEggService.
 */
@Service
public class CrackEggService {

    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private com.kingsfarm.kingsfarmbackend.backfill.EditWindowService editWindow;

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final CrackEggStateRepository stateRepository;
    private final GcSaleTransactionRepository saleRepository;
    private final CrackEggGiftLogRepository giftLogRepository;
    private final OpeningStockLockService lockService;
    private final ProductionService productionService;

    public CrackEggService(CrackEggStateRepository stateRepository,
                            GcSaleTransactionRepository saleRepository,
                            CrackEggGiftLogRepository giftLogRepository,
                            OpeningStockLockService lockService,
                            ProductionService productionService) {
        this.stateRepository = stateRepository;
        this.saleRepository = saleRepository;
        this.giftLogRepository = giftLogRepository;
        this.lockService = lockService;
        this.productionService = productionService;
    }

    @Transactional
    public CrackEggState state() {
        return stateRepository.findById(CrackEggState.SINGLETON_ID).orElseGet(() -> {
            CrackEggState state = new CrackEggState();
            return stateRepository.save(state);
        });
    }

    /**
     * Read-only twin of {@link #state} — same reason as MortalityService's
     * peekCategoryValue / FeedMillService's peekFishFeedStock: calling
     * state() here would be a self-invocation that bypasses Spring's
     * @Transactional proxy, so its write would run inside the caller's
     * @Transactional(readOnly = true) instead and throw "Connection is
     * read-only" the first time this is read before any Crack Egg action
     * has ever been saved. Returns an unsaved, default-valued instance
     * instead of ever writing from a read path.
     */
    private CrackEggState peekState() {
        return stateRepository.findById(CrackEggState.SINGLETON_ID).orElseGet(CrackEggState::new);
    }

    // ── Cross-module feed: Crack Egg → Production ───────────────────────────

    @Transactional(readOnly = true)
    public double goodGiftQty() {
        return peekState().getGcGiftQty();
    }

    @Transactional(readOnly = true)
    public double goodSalesQty() {
        return goodSalesQty(LocalDate.now());
    }

    @Transactional(readOnly = true)
    public double goodSalesQty(LocalDate day) {
        return saleRepository.sumQtyBetween(com.kingsfarm.kingsfarmbackend.common.AppDay.start(day), com.kingsfarm.kingsfarmbackend.common.AppDay.end(day));
    }

    /** Good Crack gifts, or Rough Crack sent to the feed mill, that belong to a finished day: added to that day, later days follow. */
    @Audited(module = Mod.CRACK_EGG, action = "Record Past-day Crack Movement", type = LogType.AUDIT, detail = "#request.kind() + ' for ' + #request.date()")
    @Transactional
    public void recordPastAdjustment(com.kingsfarm.kingsfarmbackend.crackegg.dto.PastCrackAdjustmentRequest request) {
        LocalDate day = resolveEntryDay(request.date());
        if (day.isEqual(LocalDate.now())) {
            throw new BadRequestException("For today, use the normal fields.");
        }
        // A negative quantity takes away something entered by mistake or lowers an earlier figure for that day.
        if (request.qty() == 0) {
            throw new BadRequestException("Enter a quantity.");
        }
        if (request.kind() == com.kingsfarm.kingsfarmbackend.crackegg.dto.PastCrackAdjustmentRequest.Kind.GOOD_GIFT) {
            productionService.carryCrackDelta(day, -request.qty(), 0);
        } else {
            productionService.carryCrackDelta(day, 0, -request.qty());
        }
    }

    /** System adjustment from a past-day edit: shifts the live Good/Rough openings (ignores the opening-stock lock). */
    @Transactional
    public void adjustOpenings(double goodDelta, double roughDelta) {
        CrackEggState s = state();
        s.setGcOpening(s.getGcOpening() + goodDelta);
        s.setRcOpening(s.getRcOpening() + roughDelta);
        stateRepository.save(s);
    }

    private LocalDate resolveEntryDay(LocalDate requested) {
        LocalDate today = LocalDate.now();
        LocalDate day = requested == null ? today : requested;
        if (day.isAfter(today)) {
            throw new BadRequestException("You can't enter records for a future date.");
        }
        if (!day.isEqual(today) && !editWindow.isOpen(Mod.CRACK_EGG, day)) {
            throw new ForbiddenException("This day isn't open for entry. Days must be entered in order - the banner at the top shows the day to work on. A day that is already closed needs the Administrator's permission.");
        }
        return day;
    }

    /** Daily rollover — finished-day Good/Rough closing becomes the new (locked) opening; gift/sales/feed-mill start at zero. */
    @Transactional
    public void rollOver(LocalDate prevDay) {
        CrackEggState s = state();
        ProductionDayState prod = productionService.dayStateFor(prevDay);
        double gcProduced = prod == null ? 0 : prod.getCrackGoodProd();
        double gcReceived = prod == null ? 0 : prod.getGoodClassify();
        double rcProduced = prod == null ? 0 : prod.getCrackRoughProd();
        double rcReceived = prod == null ? 0 : prod.getRoughClassify();
        double gcClosing = s.getGcOpening() + gcProduced + gcReceived - s.getGcGiftQty() - goodSalesQty(prevDay);
        double rcClosing = s.getRcOpening() + rcProduced + rcReceived - s.getRcFeedMill();
        s.setGcOpening(gcClosing);
        s.setRcOpening(rcClosing);
        s.setGcGiftQty(0);
        s.setGcGiftRecipient("");
        s.setGcGiftAuthorizer("");
        s.setRcFeedMill(0);
        stateRepository.save(s);
        if (!editWindow.isCatchingUp(Mod.CRACK_EGG)) {
            lockService.lock(Mod.CRACK_EGG, "good");
            lockService.lock(Mod.CRACK_EGG, "rough");
        }
    }

    // ── Stock summary ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public StockSummaryResponse stockSummary() {
        CrackEggState s = peekState();
        ProductionDayState prod = productionService.getTodayDayState();
        double gcProduced = prod.getCrackGoodProd();
        double gcReceived = prod.getGoodClassify();
        double rcProduced = prod.getCrackRoughProd();
        double rcReceived = prod.getRoughClassify();

        double gcTotalSales = goodSalesQty();
        double gcRevenue = saleRepository.sumRevenueBetween(com.kingsfarm.kingsfarmbackend.common.AppDay.start(LocalDate.now()), com.kingsfarm.kingsfarmbackend.common.AppDay.end(LocalDate.now()));
        double gcClosing = s.getGcOpening() + gcProduced + gcReceived - s.getGcGiftQty() - gcTotalSales;
        double rcClosing = s.getRcOpening() + rcProduced + rcReceived - s.getRcFeedMill();

        return new StockSummaryResponse(
                s.getGcOpening(), lockService.isLocked(Mod.CRACK_EGG, "good"), gcProduced, gcReceived,
                s.getGcSellingPrice(), s.getGcGiftQty(), s.getGcGiftRecipient(), s.getGcGiftAuthorizer(),
                gcTotalSales, gcRevenue, gcClosing,
                s.getRcOpening(), lockService.isLocked(Mod.CRACK_EGG, "rough"), rcProduced, rcReceived,
                s.getRcFeedMill(), rcClosing
        );
    }

    /**
     * Stock for the day being worked on. Today = the live figures. A finished day = that day's own opening
     * (kept per day), what was produced/classified into it, its sales, and its closing - which is by
     * construction the next day's opening - so gifts and Feed Mill usage are whatever is left between them.
     */
    @Transactional(readOnly = true)
    public StockSummaryResponse stockSummary(LocalDate date) {
        if (date == null || !date.isBefore(LocalDate.now())) {
            return stockSummary();
        }
        ProductionDayState day = productionService.dayStateFor(date);
        ProductionDayState next = productionService.nextDayStateAfter(date);
        if (day == null || next == null) {
            return stockSummary();
        }
        CrackEggState s = peekState();
        double gcOpening = day.getCrackGoodOpen();
        double rcOpening = day.getCrackRoughOpen();
        double gcProduced = day.getCrackGoodProd();
        double gcReceived = day.getGoodClassify();
        double rcProduced = day.getCrackRoughProd();
        double rcReceived = day.getRoughClassify();
        double gcSales = goodSalesQty(date);
        double gcRevenue = saleRepository.sumRevenueBetween(com.kingsfarm.kingsfarmbackend.common.AppDay.start(date), com.kingsfarm.kingsfarmbackend.common.AppDay.end(date));
        double gcClosing = next.getCrackGoodOpen();
        double rcClosing = next.getCrackRoughOpen();
        double gcGift = Math.max(gcOpening + gcProduced + gcReceived - gcSales - gcClosing, 0);
        double rcFeedMill = Math.max(rcOpening + rcProduced + rcReceived - rcClosing, 0);
        return new StockSummaryResponse(
                gcOpening, lockService.isLocked(Mod.CRACK_EGG, "good"), gcProduced, gcReceived,
                s.getGcSellingPrice(), gcGift, "", "",
                gcSales, gcRevenue, gcClosing,
                rcOpening, lockService.isLocked(Mod.CRACK_EGG, "rough"), rcProduced, rcReceived,
                rcFeedMill, rcClosing
        );
    }

    // ── Good Crack scalar fields ─────────────────────────────────────────

    @Audited(module = Mod.CRACK_EGG, action = "Update Opening Stock", detail = "'Good Crack -> ' + #request.value()")
    @Transactional
    public void setGcOpening(UpdateIntValueRequest request, String username) {
        if (lockService.isLocked(Mod.CRACK_EGG, "good")) {
            throw new ForbiddenException("Opening Stock is locked. Submit an Opening Stock request for the Administrator to approve.");
        }
        if (request.date() != null && request.date().isBefore(LocalDate.now())) {
            resolveEntryDay(request.date());
            productionService.setCrackOpeningForDay(true, request.value(), request.date(), username);
            return;
        }
        CrackEggState s = state();
        s.setGcOpening(request.value());
        stateRepository.save(s);
    }

    @Transactional
    public void lockGcOpening() {
        lockService.lock(Mod.CRACK_EGG, "good");
    }

    @Audited(module = Mod.CRACK_EGG, action = "Update Opening Stock", detail = "'Rough Crack -> ' + #request.value()")
    @Transactional
    public void setRcOpening(UpdateIntValueRequest request, String username) {
        if (lockService.isLocked(Mod.CRACK_EGG, "rough")) {
            throw new ForbiddenException("Opening Stock is locked. Submit an Opening Stock request for the Administrator to approve.");
        }
        if (request.date() != null && request.date().isBefore(LocalDate.now())) {
            resolveEntryDay(request.date());
            productionService.setCrackOpeningForDay(false, request.value(), request.date(), username);
            return;
        }
        CrackEggState s = state();
        s.setRcOpening(request.value());
        stateRepository.save(s);
    }

    @Transactional
    public void lockRcOpening() {
        lockService.lock(Mod.CRACK_EGG, "rough");
    }

    @Transactional
    public void setGcSellingPrice(UpdateLongValueRequest request) {
        CrackEggState s = state();
        s.setGcSellingPrice(request.value());
        stateRepository.save(s);
    }

    // Gift qty/recipient/authorizer update live, no validation blocking — matches
    // the frontend, which only shows a visual warning (hasError) if gift qty
    // exceeds available stock but never disables saving.
    @Transactional
    public void setGcGiftQty(UpdateIntValueRequest request) {
        CrackEggState s = state();
        s.setGcGiftQty(request.value());
        stateRepository.save(s);
    }

    @Transactional
    public void setGcGiftRecipient(UpdateTextValueRequest request) {
        CrackEggState s = state();
        s.setGcGiftRecipient(request.value());
        stateRepository.save(s);
    }

    @Transactional
    public void setGcGiftAuthorizer(UpdateTextValueRequest request) {
        CrackEggState s = state();
        s.setGcGiftAuthorizer(request.value());
        stateRepository.save(s);
    }

    /** Unlike Gift, this IS hard-validated — the frontend disables its Save button once usage exceeds available Rough Crack stock. */
    @Audited(module = Mod.CRACK_EGG, action = "Save Feed Mill Usage", detail = "'Rough Crack -> ' + #request.value()")
    @Transactional
    public void setRcFeedMill(UpdateIntValueRequest request) {
        CrackEggState s = state();
        ProductionDayState prod = productionService.getTodayDayState();
        double available = s.getRcOpening() + prod.getCrackRoughProd() + prod.getRoughClassify();
        if (request.value() > available) {
            throw new BadRequestException("Cannot exceed available Rough Crack stock (" + available + ").");
        }
        s.setRcFeedMill(request.value());
        stateRepository.save(s);
    }

    // ── Good Crack sales ──────────────────────────────────────────────────

    @Audited(module = Mod.CRACK_EGG, action = "Record Sale", type = LogType.AUDIT, detail = "'Customer: ' + #request.customer()")
    @Transactional
    public GcSaleTransaction createSale(CreateGcSaleRequest request, String username) {
        validatePaymentMethods(request.paymentMethods(), request.bank());
        LocalDate day = resolveEntryDay(request.date());
        boolean past = !day.isEqual(LocalDate.now());

        StockSummaryResponse summary = stockSummary(day);
        if (request.qty() > summary.gcClosing()) {
            throw new BadRequestException("Cannot sell " + request.qty() + " — only " + summary.gcClosing() + " Good Crack available.");
        }

        long cash = request.paymentMethods().contains(PaymentMethod.CASH) ? request.cashAmount() : 0;
        long transfer = request.paymentMethods().contains(PaymentMethod.TRANSFER) ? request.transferAmount() : 0;

        GcSaleTransaction txn = GcSaleTransaction.builder()
                .customer(request.customer()).state(request.state() == null ? "" : request.state()).qty(request.qty()).price(request.price())
                .paymentMethods(new HashSet<>(request.paymentMethods()))
                .bank(request.paymentMethods().contains(PaymentMethod.TRANSFER) ? request.bank() : null)
                .cashAmount(cash).transferAmount(transfer).amountPaid(cash + transfer)
                .credit(request.credit()).advance(request.advance())
                .enteredBy(username)
                .build();
        if (past) {
            txn.setOccurredAt(day.atTime(12, 0).atZone(ZONE).toInstant());
        }
        GcSaleTransaction saved = saleRepository.save(txn);
        if (past) {
            productionService.carryCrackDelta(day, -request.qty(), 0);
        }
        return saved;
    }

    @Audited(module = Mod.CRACK_EGG, action = "Edit Sale", type = LogType.AUDIT, detail = "'Transaction #' + #id")
    @Transactional
    public GcSaleTransaction updateSale(Long id, UpdateGcSaleRequest request, String username) {
        GcSaleTransaction txn = saleRepository.findById(id).orElseThrow(() -> new NotFoundException("Transaction not found."));
        if (!isEditable(txn.getOccurredAt())) {
            throw new ForbiddenException("This day isn't open for entry. Days must be entered in order - the banner at the top shows the day to work on. A day that is already closed needs the Administrator's permission.");
        }
        double oldQty = txn.getQty();
        txn.setCustomer(request.customer());
        txn.setState(request.state());
        txn.setQty(request.qty());
        txn.setPrice(request.price());
        txn.setCredit(request.credit());
        txn.setAdvance(request.advance());
        txn.setUpdatedBy(username);
        GcSaleTransaction saved = saleRepository.save(txn);
        LocalDate savedDay = saved.getOccurredAt().atZone(ZONE).toLocalDate();
        if (savedDay.isBefore(LocalDate.now())) {
            productionService.carryCrackDelta(savedDay, oldQty - request.qty(), 0);
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<GcSaleTransaction> saleHistory(Pageable pageable) {
        return saleRepository.findAllByOrderByOccurredAtDesc(pageable);
    }

    // ── Gift log ──────────────────────────────────────────────────────────

    /** Snapshots the CURRENT gcGiftQty/recipient/authorizer into a permanent log row — matches the frontend's saveGift(), which reads live state rather than taking new values as input. */
    @Audited(module = Mod.CRACK_EGG, action = "Save Gift", type = LogType.AUDIT)
    @Transactional
    public CrackEggGiftLogEntry saveGiftSnapshot(String username) {
        CrackEggState s = state();
        CrackEggGiftLogEntry entry = CrackEggGiftLogEntry.builder()
                .qty(s.getGcGiftQty()).recipient(s.getGcGiftRecipient()).authorizer(s.getGcGiftAuthorizer())
                .enteredBy(username)
                .build();
        return giftLogRepository.save(entry);
    }

    @Audited(module = Mod.CRACK_EGG, action = "Edit Gift Log Entry", type = LogType.AUDIT, detail = "'Entry #' + #id")
    @Transactional
    public CrackEggGiftLogEntry updateGiftEntry(Long id, UpdateGiftLogEntryRequest request, String username) {
        CrackEggGiftLogEntry entry = giftLogRepository.findById(id).orElseThrow(() -> new NotFoundException("Gift log entry not found."));
        if (!isEditable(entry.getOccurredAt())) {
            throw new ForbiddenException("This day isn't open for entry. Days must be entered in order - the banner at the top shows the day to work on. A day that is already closed needs the Administrator's permission.");
        }
        entry.setQty(request.qty());
        entry.setRecipient(request.recipient());
        entry.setAuthorizer(request.authorizer());
        entry.setUpdatedBy(username);
        return giftLogRepository.save(entry);
    }

    @org.springframework.beans.factory.annotation.Value("${app.transactions.delete-enabled:false}")
    private boolean transactionChangeEnabled;

    public boolean isTransactionChangeEnabled() {
        return transactionChangeEnabled;
    }

    /**
     * Administrator-only (switched by TRANSACTION_DELETE_ENABLED): a gift that was saved on the wrong day is moved to the
     * day it belongs to. The days in between get the gift taken off their closing stock (and so off the next day's opening);
     * days after the gift's old day already had it, so they are not touched twice.
     */
    @Audited(module = Mod.CRACK_EGG, action = "Move Gift To Another Day", type = LogType.AUDIT, detail = "'Entry #' + #id + ' -> ' + #newDay")
    @Transactional
    public CrackEggGiftLogEntry moveGiftDate(Long id, LocalDate newDay) {
        if (!transactionChangeEnabled) {
            throw new ForbiddenException("Changing transactions is switched off.");
        }
        LocalDate today = LocalDate.now();
        if (newDay == null || !newDay.isBefore(today)) {
            throw new BadRequestException("Choose a finished day (before today).");
        }
        CrackEggGiftLogEntry entry = giftLogRepository.findById(id).orElseThrow(() -> new NotFoundException("Gift log entry not found."));
        LocalDate oldDay = entry.getOccurredAt().atZone(ZONE).toLocalDate();
        if (oldDay.equals(newDay)) {
            return entry;
        }
        double qty = entry.getQty();
        if (oldDay.isEqual(today)) {
            // Still in today's live figure: take it out of today, then apply it to the finished day and everything after.
            CrackEggState s = state();
            s.setGcGiftQty(Math.max(s.getGcGiftQty() - qty, 0));
            stateRepository.save(s);
            productionService.carryCrackDelta(newDay, -qty, 0);
        } else if (newDay.isBefore(oldDay)) {
            productionService.shiftGoodCrackOpenings(newDay, oldDay, -qty);
        } else {
            productionService.shiftGoodCrackOpenings(oldDay, newDay, qty);
        }
        entry.setOccurredAt(newDay.atTime(12, 0).atZone(ZONE).toInstant());
        entry.setUpdatedBy("administrator");
        return giftLogRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public Page<CrackEggGiftLogEntry> giftLogHistory(Pageable pageable) {
        return giftLogRepository.findAllByOrderByOccurredAtDesc(pageable);
    }

    // ── Reports (BACKEND_PLAN.md §8) ────────────────────────────────────────
    // roughToFeed/closing are deliberately not columns here — CrackEggState is
    // a live running total with no per-day history (see §8's gap note), so
    // there's nothing real to sum for either.

    private static final List<ReportColumn> REPORT_COLUMNS = List.of(
            new ReportColumn("goodSales", "Good Crack Sales (qty)", true),
            new ReportColumn("goodRevenue", "Good Crack Revenue", false),
            new ReportColumn("gifts", "Gifted", true),
            new ReportColumn("goodReceived", "Good Crack Received", true),
            new ReportColumn("roughReceived", "Rough Crack Received", true),
            new ReportColumn("goodOpening", "Good Crack Opening", true),
            new ReportColumn("goodClosing", "Good Crack Closing", true),
            new ReportColumn("roughOpening", "Rough Crack Opening", true),
            new ReportColumn("roughClosing", "Rough Crack Closing", true)
    );

    private static void putStock(Map<String, Object> row, double[] s) {
        row.put("goodOpening", s[0]);
        row.put("goodClosing", s[1]);
        row.put("roughOpening", s[2]);
        row.put("roughClosing", s[3]);
    }

    @Transactional(readOnly = true)
    public ReportTableResponse dailyReport(LocalDate start, LocalDate end) {
        Instant rangeStart = ReportPeriods.startOfDay(start);
        Instant rangeEnd = ReportPeriods.startOfNextDay(end);
        Map<LocalDate, List<GcSaleTransaction>> salesByDate = salesInRange(rangeStart, rangeEnd).stream()
                .collect(Collectors.groupingBy(t -> t.getOccurredAt().atZone(ZONE).toLocalDate()));
        Map<LocalDate, List<CrackEggGiftLogEntry>> giftsByDate = giftsInRange(rangeStart, rangeEnd).stream()
                .collect(Collectors.groupingBy(g -> g.getOccurredAt().atZone(ZONE).toLocalDate()));
        Map<LocalDate, double[]> received = productionService.crackReceivedBetween(start, end);
        double[] none = new double[]{0, 0};
        Map<LocalDate, double[]> stockByDay = productionService.crackStockByDay(start, end);
        double[] noStock = new double[]{0, 0, 0, 0};
        List<Map<String, Object>> rows = ReportPeriods.daysBetween(start, end).stream()
                .map(date -> {
                    Map<String, Object> row = reportRow(ReportPeriods.dayLabel(date), salesByDate.getOrDefault(date, List.of()), giftsByDate.getOrDefault(date, List.of()), received.getOrDefault(date, none));
                    putStock(row, stockByDay.getOrDefault(date, noStock));
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
        Map<YearMonth, List<GcSaleTransaction>> salesByMonth = salesInRange(rangeStart, rangeEnd).stream()
                .collect(Collectors.groupingBy(t -> YearMonth.from(t.getOccurredAt().atZone(ZONE).toLocalDate())));
        Map<YearMonth, List<CrackEggGiftLogEntry>> giftsByMonth = giftsInRange(rangeStart, rangeEnd).stream()
                .collect(Collectors.groupingBy(g -> YearMonth.from(g.getOccurredAt().atZone(ZONE).toLocalDate())));
        Map<YearMonth, double[]> receivedByMonth = new java.util.HashMap<>();
        productionService.crackReceivedBetween(monthsList.get(0).atDay(1), monthsList.get(monthsList.size() - 1).atEndOfMonth()).forEach((day, v) -> {
            double[] m = receivedByMonth.computeIfAbsent(YearMonth.from(day), k -> new double[]{0, 0});
            m[0] += v[0];
            m[1] += v[1];
        });
        double[] none = new double[]{0, 0};
        Map<LocalDate, double[]> stockByDay = productionService.crackStockByDay(monthsList.get(0).atDay(1), monthsList.get(monthsList.size() - 1).atEndOfMonth());
        List<Map<String, Object>> rows = monthsList.stream()
                .map(month -> {
                    Map<String, Object> row = reportRow(ReportPeriods.monthLabel(month), salesByMonth.getOrDefault(month, List.of()), giftsByMonth.getOrDefault(month, List.of()), receivedByMonth.getOrDefault(month, none));
                    putStock(row, ProductionService.monthEnds(stockByDay, month, 4, 0, 2));
                    return row;
                })
                .toList();
        return new ReportTableResponse(REPORT_COLUMNS, rows);
    }

    private List<GcSaleTransaction> salesInRange(Instant start, Instant end) {
        return saleRepository.findAllByOccurredAtGreaterThanEqualAndOccurredAtLessThan(start, end);
    }

    private List<CrackEggGiftLogEntry> giftsInRange(Instant start, Instant end) {
        return giftLogRepository.findAllByOccurredAtGreaterThanEqualAndOccurredAtLessThan(start, end);
    }

    private Map<String, Object> reportRow(String periodLabel, List<GcSaleTransaction> sales, List<CrackEggGiftLogEntry> gifts, double[] received) {
        double goodSales = sales.stream().mapToDouble(GcSaleTransaction::getQty).sum();
        double goodRevenue = sales.stream().mapToDouble(t -> t.getQty() * t.getPrice()).sum();
        double giftQty = gifts.stream().mapToDouble(CrackEggGiftLogEntry::getQty).sum();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("period", periodLabel);
        row.put("goodSales", goodSales);
        row.put("goodRevenue", goodRevenue);
        row.put("gifts", giftQty);
        row.put("goodReceived", received[0]);
        row.put("roughReceived", received[1]);
        return row;
    }

    private void validatePaymentMethods(List<PaymentMethod> methods, String bank) {
        if (methods == null || methods.isEmpty()) {
            throw new BadRequestException("Select at least one payment method.");
        }
        if (methods.contains(PaymentMethod.TRANSFER) && (bank == null || bank.isBlank())) {
            throw new BadRequestException("Please select the bank the customer transferred to.");
        }
    }

    public boolean isEditable(Instant occurredAt) {
        return editWindow.isOpen(Mod.CRACK_EGG, occurredAt.atZone(ZONE).toLocalDate());
    }
}
