package com.kingsfarm.kingsfarmbackend.production;

import com.kingsfarm.kingsfarmbackend.audit.Audited;
import com.kingsfarm.kingsfarmbackend.birdstock.BirdPenRecord;
import com.kingsfarm.kingsfarmbackend.birdstock.BirdPenRecordRepository;
import com.kingsfarm.kingsfarmbackend.birdstock.Pen;
import com.kingsfarm.kingsfarmbackend.birdstock.PenRepository;
import com.kingsfarm.kingsfarmbackend.common.CatKey;
import com.kingsfarm.kingsfarmbackend.common.Mod;
import com.kingsfarm.kingsfarmbackend.common.exception.ForbiddenException;
import com.kingsfarm.kingsfarmbackend.common.exception.NotFoundException;
import com.kingsfarm.kingsfarmbackend.common.reports.ReportColumn;
import com.kingsfarm.kingsfarmbackend.common.reports.ReportPeriods;
import com.kingsfarm.kingsfarmbackend.common.reports.ReportTableResponse;
import com.kingsfarm.kingsfarmbackend.openingstock.OpeningStockLockService;
import com.kingsfarm.kingsfarmbackend.production.dto.CategoryStockRow;
import com.kingsfarm.kingsfarmbackend.production.dto.ProductionDayStateResponse;
import com.kingsfarm.kingsfarmbackend.production.dto.UpdateCatOpeningRequest;
import com.kingsfarm.kingsfarmbackend.production.dto.UpdateCrackFieldsRequest;
import com.kingsfarm.kingsfarmbackend.production.dto.UpdatePenEntryRequest;
import com.kingsfarm.kingsfarmbackend.crackegg.CrackEggService;
import com.kingsfarm.kingsfarmbackend.wholeegg.WholeEggService;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Backs ProductionView's Whole Egg tab (Production by Pen + Category Stock
 * Summary) and Crack Egg tab. One {@link ProductionDayState} row per day
 * covers every scalar field; one {@link ProductionPenEntry} row per
 * (pen, day) covers the per-pen production matrix. Both share the same
 * same-day edit rule as Bird Stock (see BirdStockService's javadoc for why
 * the frontend's separate save/unlock toggle collapses into that one rule
 * here).
 *
 * wholeEggCrackUse/wholeEggTotalSales/wholeEggGift read live from
 * WholeEggService (BACKEND_PLAN.md §6: Whole Egg → Production). That
 * dependency is genuinely bidirectional — WholeEggService also reads this
 * class's catProdTotals for its own Stock Overview (Production → Whole Egg:
 * Total Production → Egg Production) — so wholeEggService is injected
 * {@code @Lazy} here to break the constructor-injection cycle; whichever
 * side gets constructed first no longer needs the other to already exist.
 *
 * crackEggGoodGift/crackEggGoodSales read live from CrackEggService (§6:
 * Crack Egg → Production) — also genuinely bidirectional (CrackEggService
 * reads this class's today's ProductionDayState for its own Good/Rough
 * Crack Production & Received figures), so crackEggService is injected
 * {@code @Lazy} here too, same reasoning as wholeEggService.
 */
@Service
public class ProductionService {

    private final PenRepository penRepository;
    private final BirdPenRecordRepository birdPenRecordRepository;
    private final ProductionPenEntryRepository penEntryRepository;
    private final ProductionDayStateRepository dayStateRepository;
    private final OpeningStockLockService lockService;
    private final WholeEggService wholeEggService;
    private final CrackEggService crackEggService;

    public ProductionService(PenRepository penRepository,
                              BirdPenRecordRepository birdPenRecordRepository,
                              ProductionPenEntryRepository penEntryRepository,
                              ProductionDayStateRepository dayStateRepository,
                              OpeningStockLockService lockService,
                              @Lazy WholeEggService wholeEggService,
                              @Lazy CrackEggService crackEggService) {
        this.penRepository = penRepository;
        this.birdPenRecordRepository = birdPenRecordRepository;
        this.penEntryRepository = penEntryRepository;
        this.dayStateRepository = dayStateRepository;
        this.lockService = lockService;
        this.wholeEggService = wholeEggService;
        this.crackEggService = crackEggService;
    }

    // ── Production by Pen ────────────────────────────────────────────────────

    @Transactional
    public List<ProductionPenEntry> getTodayPenEntries() {
        LocalDate today = LocalDate.now();
        return penRepository.findAllByActiveTrueOrderByNameAsc().stream()
                .map(pen -> findOrCreateTodayEntry(pen, today))
                .toList();
    }

    private ProductionPenEntry findOrCreateTodayEntry(Pen pen, LocalDate today) {
        return penEntryRepository.findByPenAndEntryDate(pen, today)
                .orElseGet(() -> penEntryRepository.save(ProductionPenEntry.builder().pen(pen).entryDate(today).build()));
    }

    @Audited(module = Mod.PRODUCTION, action = "Save Production Entry", detail = "'Pen #' + #penId + ' / ' + #request.category() + ' = ' + #request.qty()")
    @Transactional
    public ProductionPenEntry updatePenEntry(Long penId, UpdatePenEntryRequest request, String username) {
        Pen pen = penRepository.findById(penId).orElseThrow(() -> new NotFoundException("Pen not found."));
        LocalDate today = LocalDate.now();
        ProductionPenEntry entry = findOrCreateTodayEntry(pen, today);
        if (!entry.getEntryDate().isEqual(today)) {
            throw new ForbiddenException("This record was saved on a previous day and can no longer be edited.");
        }
        entry.set(request.category(), request.qty());
        if (entry.getEnteredBy() == null) {
            entry.setEnteredBy(username);
        } else {
            entry.setUpdatedBy(username);
        }
        return penEntryRepository.save(entry);
    }

    public int birdClosingFor(Pen pen, LocalDate date) {
        return birdPenRecordRepository.findByPenAndEntryDate(pen, date).map(BirdPenRecord::closing).orElse(0);
    }

    public String productionPercent(int totalCrates, int birdClosing) {
        if (birdClosing == 0) {
            return "—";
        }
        double pct = (totalCrates * 30.0 / birdClosing) * 100.0;
        return String.format(Locale.US, "%.1f%%", pct);
    }

    public boolean isEditable(LocalDate entryDate) {
        return entryDate.isEqual(LocalDate.now());
    }

    /** Sum of every active pen's production today, per category — feeds Category Stock Summary's "Total Production" column. */
    @Transactional(readOnly = true)
    public java.util.Map<CatKey, Integer> catProdTotals(LocalDate date) {
        java.util.Map<CatKey, Integer> totals = new java.util.EnumMap<>(CatKey.class);
        for (CatKey k : CatKey.values()) {
            totals.put(k, 0);
        }
        for (ProductionPenEntry entry : penEntryRepository.findAllByEntryDate(date)) {
            for (CatKey k : CatKey.values()) {
                totals.merge(k, entry.get(k), Integer::sum);
            }
        }
        return totals;
    }

    // ── Cross-module auto-feeds ──────────────────────────────────────────────

    private int wholeEggCrackUse(CatKey category) {
        return wholeEggService.salesCrackFor(category);
    }

    private int wholeEggTotalSales(CatKey category) {
        return wholeEggService.totalSalesFor(category);
    }

    private int wholeEggGift(CatKey category) {
        return wholeEggService.giftFor(category);
    }

    private int crackEggGoodGift() {
        return crackEggService.goodGiftQty();
    }

    private int crackEggGoodSales() {
        return crackEggService.goodSalesQty();
    }

    // ── Day state (Category Stock Summary + Crack Egg tab) ──────────────────

    @Transactional
    public ProductionDayState getTodayDayState() {
        LocalDate today = LocalDate.now();
        return dayStateRepository.findByEntryDate(today).orElseGet(() -> {
            ProductionDayState previous = dayStateRepository.findFirstByEntryDateLessThanOrderByEntryDateDesc(today).orElse(null);
            ProductionDayState.ProductionDayStateBuilder builder = ProductionDayState.builder().entryDate(today);
            if (previous != null) {
                LocalDate prevDate = previous.getEntryDate();
                for (CatKey k : CatKey.values()) {
                    int prevProd = catProdTotals(prevDate).get(k);
                    int prevClosing = previous.catOpening(k) + prevProd - wholeEggCrackUse(k) - wholeEggTotalSales(k) - wholeEggGift(k);
                    builder = switch (k) {
                        case X_LARGE -> builder.catOpeningXl(prevClosing);
                        case LARGE -> builder.catOpeningLg(prevClosing);
                        case MEDIUM -> builder.catOpeningMd(prevClosing);
                        case SMALL -> builder.catOpeningSm(prevClosing);
                        case PULLET -> builder.catOpeningPl(prevClosing);
                        case WHITE -> builder.catOpeningWh(prevClosing);
                    };
                }
                int prevCrackGoodClosing = (int) Math.round(previous.getCrackGoodOpen() + previous.getCrackGoodProd() + previous.getGoodClassify() - crackEggGoodGift() - crackEggGoodSales());
                int prevCrackRoughClosing = previous.getCrackRoughOpen() + previous.getCrackRoughProd() + (int) Math.round(previous.getRoughClassify());
                builder = builder.crackGoodOpen(prevCrackGoodClosing).crackRoughOpen(prevCrackRoughClosing);
            }
            return dayStateRepository.save(builder.build());
        });
    }

    @Audited(module = Mod.PRODUCTION, action = "Update Category Opening Stock", detail = "#request.category() + ' -> ' + #request.value()")
    @Transactional
    public ProductionDayState updateCatOpening(UpdateCatOpeningRequest request, String username) {
        ProductionDayState state = getTodayDayState();
        if (!isEditable(state.getEntryDate())) {
            throw new ForbiddenException("This record was saved on a previous day and can no longer be edited.");
        }
        if (lockService.isLocked(Mod.PRODUCTION, request.category().wire())) {
            throw new ForbiddenException("Opening Stock is locked. Submit an Opening Stock request for the Administrator to approve.");
        }
        state.setCatOpening(request.category(), request.value());
        stamp(state, username);
        return dayStateRepository.save(state);
    }

    @Transactional
    public void lockCatOpening(CatKey category) {
        lockService.lock(Mod.PRODUCTION, category.wire());
    }

    @Audited(module = Mod.PRODUCTION, action = "Save Crack Egg Fields")
    @Transactional
    public ProductionDayState updateCrackFields(UpdateCrackFieldsRequest request, String username) {
        ProductionDayState state = getTodayDayState();
        if (!isEditable(state.getEntryDate())) {
            throw new ForbiddenException("This record was saved on a previous day and can no longer be edited.");
        }
        if (request.crackGoodOpen() != null) state.setCrackGoodOpen(request.crackGoodOpen());
        if (request.crackRoughOpen() != null) state.setCrackRoughOpen(request.crackRoughOpen());
        if (request.crackGoodProd() != null) state.setCrackGoodProd(request.crackGoodProd());
        if (request.crackRoughProd() != null) state.setCrackRoughProd(request.crackRoughProd());
        if (request.goodClassify() != null) state.setGoodClassify(request.goodClassify());
        if (request.roughClassify() != null) state.setRoughClassify(request.roughClassify());
        stamp(state, username);
        return dayStateRepository.save(state);
    }

    private void stamp(ProductionDayState state, String username) {
        if (state.getEnteredBy() == null) {
            state.setEnteredBy(username);
        } else {
            state.setUpdatedBy(username);
        }
    }

    @Transactional(readOnly = true)
    public ProductionDayStateResponse toResponse(ProductionDayState state) {
        LocalDate date = state.getEntryDate();
        java.util.Map<CatKey, Integer> production = catProdTotals(date);

        List<CategoryStockRow> rows = new java.util.ArrayList<>();
        for (CatKey k : CatKey.values()) {
            int opening = state.catOpening(k);
            int prod = production.get(k);
            int crackUse = wholeEggCrackUse(k);
            int sales = wholeEggTotalSales(k);
            int gift = wholeEggGift(k);
            int closing = opening + prod - crackUse - sales - gift;
            rows.add(new CategoryStockRow(k, opening, prod, crackUse, sales, gift, closing, lockService.isLocked(Mod.PRODUCTION, k.wire())));
        }

        double totalCrackFromWhole = 0;
        for (CatKey k : CatKey.values()) {
            totalCrackFromWhole += wholeEggCrackUse(k) * k.crackWeight();
        }
        double classifyTotal = state.getGoodClassify() + state.getRoughClassify();
        boolean mismatch = Math.abs(classifyTotal - totalCrackFromWhole) > 0.01;

        int giftAuto = crackEggGoodGift();
        int salesAuto = crackEggGoodSales();
        double crackGoodClosing = state.getCrackGoodOpen() + state.getCrackGoodProd() + state.getGoodClassify() - giftAuto - salesAuto;
        int crackRoughClosing = state.getCrackRoughOpen() + state.getCrackRoughProd() + (int) Math.round(state.getRoughClassify());

        return new ProductionDayStateResponse(
                date, rows,
                state.getCrackGoodOpen(), state.getCrackRoughOpen(), state.getCrackGoodProd(), state.getCrackRoughProd(),
                state.getGoodClassify(), state.getRoughClassify(),
                (int) Math.round(totalCrackFromWhole), classifyTotal, mismatch,
                giftAuto, salesAuto, crackGoodClosing, crackRoughClosing,
                state.getEnteredBy(), state.getUpdatedBy(), isEditable(date)
        );
    }

    @Transactional(readOnly = true)
    public Page<ProductionPenEntry> penEntryHistory(Long penId, Pageable pageable) {
        if (penId != null) {
            Pen pen = penRepository.findById(penId).orElseThrow(() -> new NotFoundException("Pen not found."));
            return penEntryRepository.findAllByPenOrderByEntryDateDesc(pen, pageable);
        }
        return penEntryRepository.findAllByOrderByEntryDateDesc(pageable);
    }

    @Transactional(readOnly = true)
    public Page<ProductionDayState> dayStateHistory(Pageable pageable) {
        return dayStateRepository.findAllByOrderByEntryDateDesc(pageable);
    }

    // ── Reports (BACKEND_PLAN.md §8) ────────────────────────────────────────

    private static final List<ReportColumn> REPORT_COLUMNS = List.of(
            new ReportColumn("crates", "Crates", true),
            new ReportColumn("pct", "Prod %", true),
            new ReportColumn("xL", "XL", true),
            new ReportColumn("lg", "Large", true),
            new ReportColumn("md", "Medium", true)
    );

    /** Every pen's real closing stock in a range, summed per day — one query for the whole range rather than one per day/month (see reportRow callers below). */
    private Map<LocalDate, Integer> birdClosingByDayInRange(LocalDate start, LocalDate end) {
        return birdPenRecordRepository.findAllByEntryDateBetween(start, end).stream()
                .collect(Collectors.groupingBy(BirdPenRecord::getEntryDate, Collectors.summingInt(BirdPenRecord::closing)));
    }

    @Transactional(readOnly = true)
    public ReportTableResponse dailyReport(LocalDate start, LocalDate end) {
        List<ProductionPenEntry> entries = penEntryRepository.findAllByEntryDateBetween(start, end);
        Map<LocalDate, List<ProductionPenEntry>> byDate = entries.stream().collect(Collectors.groupingBy(ProductionPenEntry::getEntryDate));
        Map<LocalDate, Integer> birdClosingByDay = birdClosingByDayInRange(start, end);
        List<Map<String, Object>> rows = ReportPeriods.daysBetween(start, end).stream()
                .map(date -> reportRow(ReportPeriods.dayLabel(date), byDate.getOrDefault(date, List.of()), birdClosingByDay.getOrDefault(date, 0)))
                .toList();
        return new ReportTableResponse(REPORT_COLUMNS, rows);
    }

    @Transactional(readOnly = true)
    public ReportTableResponse monthlyReport(int months) {
        List<YearMonth> monthsList = ReportPeriods.trailingMonths(months);
        LocalDate start = monthsList.getFirst().atDay(1);
        LocalDate end = monthsList.getLast().atEndOfMonth();
        List<ProductionPenEntry> entries = penEntryRepository.findAllByEntryDateBetween(start, end);
        Map<YearMonth, List<ProductionPenEntry>> byMonth = entries.stream()
                .collect(Collectors.groupingBy(e -> YearMonth.from(e.getEntryDate())));
        Map<LocalDate, Integer> birdClosingByDay = birdClosingByDayInRange(start, end);
        Map<YearMonth, Integer> birdClosingByMonth = birdClosingByDay.entrySet().stream()
                .collect(Collectors.groupingBy(e -> YearMonth.from(e.getKey()), Collectors.summingInt(Map.Entry::getValue)));
        List<Map<String, Object>> rows = monthsList.stream()
                .map(month -> reportRow(ReportPeriods.monthLabel(month), byMonth.getOrDefault(month, List.of()), birdClosingByMonth.getOrDefault(month, 0)))
                .toList();
        return new ReportTableResponse(REPORT_COLUMNS, rows);
    }

    private Map<String, Object> reportRow(String periodLabel, List<ProductionPenEntry> entries, int birdClosingForPeriod) {
        int xl = entries.stream().mapToInt(ProductionPenEntry::getQtyXl).sum();
        int lg = entries.stream().mapToInt(ProductionPenEntry::getQtyLg).sum();
        int md = entries.stream().mapToInt(ProductionPenEntry::getQtyMd).sum();
        int crates = entries.stream().mapToInt(ProductionPenEntry::total).sum();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("period", periodLabel);
        row.put("crates", crates);
        row.put("pct", productionPercent(crates, birdClosingForPeriod));
        row.put("xL", xl);
        row.put("lg", lg);
        row.put("md", md);
        return row;
    }
}
