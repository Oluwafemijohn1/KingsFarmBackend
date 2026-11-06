package com.kingsfarm.kingsfarmbackend.production;

import com.kingsfarm.kingsfarmbackend.audit.Audited;
import com.kingsfarm.kingsfarmbackend.birdstock.BirdPenRecord;
import com.kingsfarm.kingsfarmbackend.birdstock.BirdPenRecordRepository;
import com.kingsfarm.kingsfarmbackend.birdstock.Pen;
import com.kingsfarm.kingsfarmbackend.birdstock.PenRepository;
import com.kingsfarm.kingsfarmbackend.common.CatKey;
import com.kingsfarm.kingsfarmbackend.common.Mod;
import com.kingsfarm.kingsfarmbackend.common.exception.ForbiddenException;
import com.kingsfarm.kingsfarmbackend.common.exception.BadRequestException;
import com.kingsfarm.kingsfarmbackend.common.exception.NotFoundException;
import com.kingsfarm.kingsfarmbackend.common.reports.ReportColumn;
import com.kingsfarm.kingsfarmbackend.common.reports.ReportPeriods;
import com.kingsfarm.kingsfarmbackend.common.reports.ReportTableResponse;
import com.kingsfarm.kingsfarmbackend.openingstock.OpeningStockLockService;
import com.kingsfarm.kingsfarmbackend.production.dto.CategoryStockRow;
import com.kingsfarm.kingsfarmbackend.production.dto.ProductionDayStateResponse;
import com.kingsfarm.kingsfarmbackend.production.dto.ProductionPenEntryResponse;
import com.kingsfarm.kingsfarmbackend.production.dto.UpdateCatOpeningRequest;
import com.kingsfarm.kingsfarmbackend.production.dto.UpdateClassifyFieldsRequest;
import com.kingsfarm.kingsfarmbackend.production.dto.UpdateCrackFieldsRequest;
import com.kingsfarm.kingsfarmbackend.production.dto.UpdatePenEntryRequest;
import com.kingsfarm.kingsfarmbackend.crackegg.CrackEggService;
import com.kingsfarm.kingsfarmbackend.wholeegg.WholeEggService;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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

    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private com.kingsfarm.kingsfarmbackend.backfill.EditWindowService editWindow;

    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private com.kingsfarm.kingsfarmbackend.backfill.DayClosureService dayClosures;

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

    /**
     * Returns response DTOs directly, resolved inside this transaction —
     * ProductionPenEntry.pen is a LAZY @ManyToOne, and
     * ProductionPenEntryResponse.from() dereferences pen.getName() (plus
     * birdClosingFor(entry.getPen(), ...) below dereferences it a second
     * time); with open-in-view disabled, doing either of those in the
     * controller after the transaction closes throws
     * LazyInitializationException (same bug class fixed in
     * OpeningStockRequestService — see its javadoc).
     */
    @Transactional
    public List<ProductionPenEntryResponse> getTodayPenEntries() {
        LocalDate today = LocalDate.now();
        return penRepository.findAllByActiveTrueOrderByNameAsc().stream()
                .map(pen -> findOrCreateTodayEntry(pen, today))
                .map(this::toPenEntryResponse)
                .toList();
    }

    /**
     * Pen entries for any day. Today is find-or-create as always; for a past
     * day only existing rows are returned unless the caller may edit that day
     * (grace or approved), in which case missing rows are created so a missed
     * day can be filled in from scratch.
     */
    @Transactional
    public List<ProductionPenEntryResponse> getPenEntriesFor(LocalDate date) {
        if (date == null || date.isEqual(LocalDate.now())) {
            return getTodayPenEntries();
        }
        boolean canEdit = isEditable(date);
        return penRepository.findAllByActiveTrueOrderByNameAsc().stream()
                .map(pen -> canEdit
                        ? java.util.Optional.of(findOrCreateTodayEntry(pen, date))
                        : penEntryRepository.findByPenAndEntryDate(pen, date))
                .flatMap(java.util.Optional::stream)
                .map(this::toPenEntryResponse)
                .toList();
    }

    private ProductionPenEntry findOrCreateTodayEntry(Pen pen, LocalDate today) {
        return penEntryRepository.findByPenAndEntryDate(pen, today)
                .orElseGet(() -> penEntryRepository.save(ProductionPenEntry.builder().pen(pen).entryDate(today).build()));
    }

    /** See getTodayPenEntries()'s javadoc — same lazy-pen mapping-inside-transaction reasoning. */
    @Audited(module = Mod.PRODUCTION, action = "Save Production Entry", detail = "'Pen #' + #penId + ' / ' + #request.category() + ' = ' + #request.qty()")
    @Transactional
    public ProductionPenEntryResponse updatePenEntry(Long penId, UpdatePenEntryRequest request, String username) {
        Pen pen = penRepository.findById(penId).orElseThrow(() -> new NotFoundException("Pen not found."));
        LocalDate today = LocalDate.now();
        LocalDate day = request.date() == null ? today : request.date();
        if (day.isAfter(today)) {
            throw new ForbiddenException("You can't enter records for a future date.");
        }
        if (!isEditable(day)) {
            throw new ForbiddenException("This day isn't open for entry. Days must be entered in order - the banner at the top shows the day to work on. A day that is already closed needs the Administrator's permission.");
        }
        ProductionPenEntry entry = findOrCreateTodayEntry(pen, day);
        double before = entry.get(request.category());
        entry.set(request.category(), request.qty());
        if (entry.getEnteredBy() == null) {
            entry.setEnteredBy(username);
        } else {
            entry.setUpdatedBy(username);
        }
        ProductionPenEntry saved = penEntryRepository.save(entry);
        if (day.isBefore(today)) {
            // A finished day's production changed, so its closing stock changed by the same amount —
            // push that difference into every later day's opening (and Whole Egg's live opening).
            carryDeltaForward(day, request.category(), request.qty() - before);
        }
        return toPenEntryResponse(saved);
    }

    /**
     * Re-calculates later days after an edit to {@code day}: every Production
     * day row after it, and today's Whole Egg opening, moves by {@code delta}.
     * Closing stock itself is always derived by the unchanged formula, so
     * shifting the openings is all that is needed.
     */
    @Transactional
    public void carryDeltaForward(LocalDate day, CatKey category, double delta) {
        if (Math.abs(delta) < 1e-9) {
            return;
        }
        for (ProductionDayState later : dayStateRepository.findAllByEntryDateGreaterThan(day)) {
            later.setCatOpening(category, later.catOpening(category) + delta);
            dayStateRepository.save(later);
        }
        wholeEggService.adjustOpening(category, delta);
    }

    /** Only ever called from within an already-@Transactional method above/below — never touches a detached entry. */
    private ProductionPenEntryResponse toPenEntryResponse(ProductionPenEntry entry) {
        int birdClosing = birdClosingFor(entry.getPen(), entry.getEntryDate());
        String pct = productionPercent(entry.total(), birdClosing);
        return ProductionPenEntryResponse.from(entry, birdClosing, pct, isEditable(entry.getEntryDate()));
    }

    public int birdClosingFor(Pen pen, LocalDate date) {
        return birdPenRecordRepository.findByPenAndEntryDate(pen, date).map(BirdPenRecord::closing).orElse(0);
    }

    public String productionPercent(double totalCrates, int birdClosing) {
        if (birdClosing == 0) {
            return "—";
        }
        double pct = (totalCrates * 30.0 / birdClosing) * 100.0;
        return String.format(Locale.US, "%.1f%%", pct);
    }

    public boolean isEditable(LocalDate entryDate) {
        return editWindow.isOpen(Mod.PRODUCTION, entryDate);
    }

    /** Sum of every active pen's production today, per category — feeds Category Stock Summary's "Total Production" column. Double: pen entries can be partial crates. */
    @Transactional(readOnly = true)
    public java.util.Map<CatKey, Double> catProdTotals(LocalDate date) {
        java.util.Map<CatKey, Double> totals = new java.util.EnumMap<>(CatKey.class);
        for (CatKey k : CatKey.values()) {
            totals.put(k, 0.0);
        }
        for (ProductionPenEntry entry : penEntryRepository.findAllByEntryDate(date)) {
            for (CatKey k : CatKey.values()) {
                totals.merge(k, entry.get(k), Double::sum);
            }
        }
        return totals;
    }

    // ── Cross-module auto-feeds ──────────────────────────────────────────────

    private double wholeEggCrackUse(CatKey category) {
        return wholeEggService.salesCrackFor(category);
    }

    private double wholeEggTotalSales(CatKey category) {
        return wholeEggService.totalSalesFor(category);
    }

    private double wholeEggTotalSales(CatKey category, LocalDate day) {
        return wholeEggService.totalSalesFor(category, day);
    }

    /** The saved day state for a specific date, or null if none exists (used by the nightly rollover). */
    @Transactional(readOnly = true)
    public ProductionDayState dayStateFor(LocalDate day) {
        return dayStateRepository.findByEntryDate(day).orElse(null);
    }

    private double wholeEggGift(CatKey category) {
        return wholeEggService.giftFor(category);
    }

    private double crackEggGoodGift() {
        return crackEggService.goodGiftQty();
    }

    private double crackEggGoodSales() {
        return crackEggService.goodSalesQty();
    }

    // ── Day state (Category Stock Summary + Crack Egg tab) ──────────────────

    // REQUIRES_NEW rather than the default REQUIRED: this is a shared
    // cross-module getOrCreate (CrackEggService.stockSummary() calls it from
    // inside its own @Transactional(readOnly = true)), and REQUIRED would
    // just join that caller's already-open transaction — which, once opened
    // read-only, stays read-only for every method that joins it, proxy or
    // not. The find-or-create write below would then hit "Connection is
    // read-only" on the first call of a fresh day. REQUIRES_NEW always opens
    // its own writable transaction regardless of the caller's, so this stays
    // safe no matter who calls it or from what transactional context.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProductionDayState getTodayDayState() {
        LocalDate today = LocalDate.now();
        return dayStateRepository.findByEntryDate(today).orElseGet(() -> {
            ProductionDayState previous = dayStateRepository.findFirstByEntryDateLessThanOrderByEntryDateDesc(today).orElse(null);
            ProductionDayState.ProductionDayStateBuilder builder = ProductionDayState.builder().entryDate(today);
            if (previous != null) {
                LocalDate prevDate = previous.getEntryDate();
                for (CatKey k : CatKey.values()) {
                    double prevProd = catProdTotals(prevDate).get(k);
                    double prevClosing = previous.catOpening(k) + prevProd - wholeEggCrackUse(k) - wholeEggTotalSales(k, prevDate) - wholeEggGift(k);
                    builder = switch (k) {
                        case X_LARGE -> builder.catOpeningXl(prevClosing);
                        case LARGE -> builder.catOpeningLg(prevClosing);
                        case MEDIUM -> builder.catOpeningMd(prevClosing);
                        case SMALL -> builder.catOpeningSm(prevClosing);
                        case PULLET -> builder.catOpeningPl(prevClosing);
                        case WHITE -> builder.catOpeningWh(prevClosing);
                    };
                }
                double prevCrackGoodClosing = previous.getCrackGoodOpen() + previous.getCrackGoodProd() + previous.getGoodClassify() - crackEggGoodGift() - crackEggService.goodSalesQty(prevDate);
                double prevCrackRoughClosing = previous.getCrackRoughOpen() + previous.getCrackRoughProd() + previous.getRoughClassify();
                builder = builder.crackGoodOpen(prevCrackGoodClosing).crackRoughOpen(prevCrackRoughClosing);
            }
            return dayStateRepository.save(builder.build());
        });
    }

    @Audited(module = Mod.PRODUCTION, action = "Update Category Opening Stock", detail = "#request.category() + ' -> ' + #request.value()")
    @Transactional
    public ProductionDayState updateCatOpening(UpdateCatOpeningRequest request, String username) {
        ProductionDayState state = dayStateForEdit(request.date());
        if (!isEditable(state.getEntryDate())) {
            throw new ForbiddenException("This day isn't open for entry. Days must be entered in order - the banner at the top shows the day to work on. A day that is already closed needs the Administrator's permission.");
        }
        if (lockService.isLocked(Mod.PRODUCTION, request.category().wire())) {
            throw new ForbiddenException("Opening Stock is locked. Submit an Opening Stock request for the Administrator to approve.");
        }
        double before = state.catOpening(request.category());
        state.setCatOpening(request.category(), request.value());
        stamp(state, username);
        ProductionDayState saved = dayStateRepository.save(state);
        if (saved.getEntryDate().isBefore(LocalDate.now())) {
            // That day's closing moved by the same amount, so every later day's opening (and Whole Egg's live opening) follows.
            carryDeltaForward(saved.getEntryDate(), request.category(), request.value() - before);
        }
        return saved;
    }

    @Transactional
    public void lockCatOpening(CatKey category) {
        lockService.lock(Mod.PRODUCTION, category.wire());
    }

    @Audited(module = Mod.PRODUCTION, action = "Save Crack Egg Fields")
    @Transactional
    public ProductionDayState updateCrackFields(UpdateCrackFieldsRequest request, String username) {
        ProductionDayState state = dayStateForEdit(request.date());
        if (!isEditable(state.getEntryDate())) {
            throw new ForbiddenException("This day isn't open for entry. Days must be entered in order - the banner at the top shows the day to work on. A day that is already closed needs the Administrator's permission.");
        }
        double goodBefore = state.getCrackGoodOpen() + state.getCrackGoodProd() + state.getGoodClassify();
        double roughBefore = state.getCrackRoughOpen() + state.getCrackRoughProd() + state.getRoughClassify();
        if (request.crackGoodOpen() != null) state.setCrackGoodOpen(request.crackGoodOpen());
        if (request.crackRoughOpen() != null) state.setCrackRoughOpen(request.crackRoughOpen());
        if (request.crackGoodProd() != null) state.setCrackGoodProd(request.crackGoodProd());
        if (request.crackRoughProd() != null) state.setCrackRoughProd(request.crackRoughProd());
        stamp(state, username);
        ProductionDayState saved = dayStateRepository.save(state);
        carryCrackIfPast(saved, goodBefore, roughBefore);
        return saved;
    }

    /**
     * Splits Total Crack Use (received from Whole Egg) into Good/Rough —
     * still writes to {@link ProductionDayState} (that's where the closing-
     * stock math for both panels already lives, so it stays put rather than
     * migrating tables), but this is the Crack Egg Manager's call, not
     * Production's, hence the separate method/DTO/endpoint with its own
     * role check ({@code @PreAuthorize("hasRole('CRACK_EGG_MANAGER')")} on
     * the controller) instead of folding it into updateCrackFields above.
     */
    @Audited(module = Mod.PRODUCTION, action = "Classify Crack Use")
    @Transactional
    public ProductionDayState updateClassifyFields(UpdateClassifyFieldsRequest request, String username) {
        ProductionDayState state = dayStateForEdit(request.date());
        if (!isEditable(state.getEntryDate())) {
            throw new ForbiddenException("This day isn't open for entry. Days must be entered in order - the banner at the top shows the day to work on. A day that is already closed needs the Administrator's permission.");
        }
        double goodBefore = state.getCrackGoodOpen() + state.getCrackGoodProd() + state.getGoodClassify();
        double roughBefore = state.getCrackRoughOpen() + state.getCrackRoughProd() + state.getRoughClassify();
        if (request.goodClassify() != null) state.setGoodClassify(request.goodClassify());
        if (request.roughClassify() != null) state.setRoughClassify(request.roughClassify());
        stamp(state, username);
        ProductionDayState saved = dayStateRepository.save(state);
        carryCrackIfPast(saved, goodBefore, roughBefore);
        return saved;
    }

    /**
     * Makes sure a Production day row exists for every day from {@code from} to {@code to}. Rows are what later days'
     * openings are carried through, so a past day edited out of the blue (for instance right after a reset) must have
     * all the days after it in place for the change to flow forward. Days created here carry the previous row's
     * closing forward from the figures that are kept per day; crack use and gifts of those days were never recorded,
     * so they count as zero.
     */
    @Transactional
    public void ensureDayRows(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (dayStateRepository.findByEntryDate(d).isPresent()) {
                continue;
            }
            if (d.isEqual(today)) {
                getTodayDayState();
                continue;
            }
            ProductionDayState prev = dayStateRepository.findFirstByEntryDateLessThanOrderByEntryDateDesc(d).orElse(null);
            ProductionDayState.ProductionDayStateBuilder builder = ProductionDayState.builder().entryDate(d);
            if (prev != null) {
                LocalDate prevDate = prev.getEntryDate();
                java.util.Map<CatKey, Double> prevProd = catProdTotals(prevDate);
                for (CatKey k : CatKey.values()) {
                    double closing = prev.catOpening(k) + prevProd.get(k) - wholeEggTotalSales(k, prevDate);
                    builder = switch (k) {
                        case X_LARGE -> builder.catOpeningXl(closing);
                        case LARGE -> builder.catOpeningLg(closing);
                        case MEDIUM -> builder.catOpeningMd(closing);
                        case SMALL -> builder.catOpeningSm(closing);
                        case PULLET -> builder.catOpeningPl(closing);
                        case WHITE -> builder.catOpeningWh(closing);
                    };
                }
                builder = builder
                        .crackGoodOpen(prev.getCrackGoodOpen() + prev.getCrackGoodProd() + prev.getGoodClassify() - crackEggService.goodSalesQty(prevDate))
                        .crackRoughOpen(prev.getCrackRoughOpen() + prev.getCrackRoughProd() + prev.getRoughClassify());
            }
            dayStateRepository.save(builder.build());
        }
    }

    /** Today's row (find-or-create) or, for a past day, the existing row — a missed day with no row at all can't be reconstructed. */
    @Transactional
    public ProductionDayState dayStateForEdit(LocalDate requested) {
        LocalDate today = LocalDate.now();
        if (requested == null || requested.isEqual(today)) {
            return getTodayDayState();
        }
        if (requested.isAfter(today)) {
            throw new ForbiddenException("You can't enter records for a future date.");
        }
        if (dayStateRepository.findByEntryDate(requested).isEmpty()) {
            // A day nobody has touched yet: lay down every day from the start date to today so changes flow forward through them.
            LocalDate start = dayClosures.startDate();
            ensureDayRows(requested.isBefore(start) ? requested : start, today);
        }
        return dayStateRepository.findByEntryDate(requested)
                .orElseThrow(() -> new NotFoundException("There is no Production record for that day."));
    }

    /** Read-only lookup for the day picker: today's row, or the existing row for a past day. */
    @Transactional
    public ProductionDayState dayStateForView(LocalDate requested) {
        return dayStateForEdit(requested);
    }

    /** Adds crack use recorded after the fact for a finished day, so that day's "to classify" figure matches. */
    @Transactional
    public void addCrackSnapshot(LocalDate day, double amount) {
        ProductionDayState row = dayStateRepository.findByEntryDate(day)
                .orElseThrow(() -> new NotFoundException("There is no Production record for that day."));
        double next = (row.getCrackFromWholeSnapshot() == null ? 0 : row.getCrackFromWholeSnapshot()) + amount;
        if (next < -1e-9) {
            throw new BadRequestException("That would make the day's crack use negative.");
        }
        row.setCrackFromWholeSnapshot(Math.max(next, 0));
        dayStateRepository.save(row);
    }

    /** Freezes Whole Egg's crack use for a finished day before the live figure resets (called by the nightly rollover). */
    @Transactional
    public void snapshotCrackUse(LocalDate day) {
        dayStateRepository.findByEntryDate(day).ifPresent(row -> {
            double total = 0;
            for (CatKey k : CatKey.values()) {
                total += wholeEggCrackUse(k);
            }
            row.setCrackFromWholeSnapshot(total);
            dayStateRepository.save(row);
        });
    }

    private void carryCrackIfPast(ProductionDayState saved, double goodBefore, double roughBefore) {
        if (!saved.getEntryDate().isBefore(LocalDate.now())) {
            return;
        }
        double goodAfter = saved.getCrackGoodOpen() + saved.getCrackGoodProd() + saved.getGoodClassify();
        double roughAfter = saved.getCrackRoughOpen() + saved.getCrackRoughProd() + saved.getRoughClassify();
        carryCrackDelta(saved.getEntryDate(), goodAfter - goodBefore, roughAfter - roughBefore);
    }

    /** A finished day's Good/Rough crack closing changed — move every later day's crack opening (and Crack Egg's live opening) by the same amount. */
    @Transactional
    public void carryCrackDelta(LocalDate day, double goodDelta, double roughDelta) {
        if (Math.abs(goodDelta) < 1e-9 && Math.abs(roughDelta) < 1e-9) {
            return;
        }
        for (ProductionDayState later : dayStateRepository.findAllByEntryDateGreaterThan(day)) {
            later.setCrackGoodOpen(later.getCrackGoodOpen() + goodDelta);
            later.setCrackRoughOpen(later.getCrackRoughOpen() + roughDelta);
            dayStateRepository.save(later);
        }
        crackEggService.adjustOpenings(goodDelta, roughDelta);
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
        java.util.Map<CatKey, Double> production = catProdTotals(date);

        List<CategoryStockRow> rows = new java.util.ArrayList<>();
        boolean pastDay = date.isBefore(LocalDate.now());
        // A finished day's closing is, by construction, the next day's opening — so a past day can be shown
        // exactly (opening, production and sales are all kept per day) with crack use and gifts combined
        // as whatever is left between them.
        ProductionDayState next = pastDay ? dayStateRepository.findFirstByEntryDateGreaterThanOrderByEntryDateAsc(date).orElse(null) : null;
        for (CatKey k : CatKey.values()) {
            double opening = state.catOpening(k);
            double prod = production.get(k);
            if (pastDay && next != null) {
                double sales = wholeEggTotalSales(k, date);
                double closing = next.catOpening(k);
                double crackAndGift = opening + prod - sales - closing;
                rows.add(new CategoryStockRow(k, opening, prod, Math.max(crackAndGift, 0), sales, 0, closing, lockService.isLocked(Mod.PRODUCTION, k.wire())));
                continue;
            }
            double crackUse = wholeEggCrackUse(k);
            double sales = wholeEggTotalSales(k);
            double gift = wholeEggGift(k);
            double closing = opening + prod - crackUse - sales - gift;
            rows.add(new CategoryStockRow(k, opening, prod, crackUse, sales, gift, closing, lockService.isLocked(Mod.PRODUCTION, k.wire())));
        }

        double totalCrackFromWhole = 0;
        if (date.isBefore(LocalDate.now())) {
            // The live figure belongs to today; a finished day uses the figure frozen at rollover.
            totalCrackFromWhole = state.getCrackFromWholeSnapshot() == null ? 0 : state.getCrackFromWholeSnapshot();
        } else {
            for (CatKey k : CatKey.values()) {
                totalCrackFromWhole += wholeEggCrackUse(k);
            }
        }
        double classifyTotal = state.getGoodClassify() + state.getRoughClassify();
        // Crack Manager classifies ONLY the Whole Egg crack use; Production classifies its own crack (crackGoodProd/crackRoughProd).
        boolean mismatch = Math.abs(classifyTotal - totalCrackFromWhole) > 0.01;

        double giftAuto = crackEggGoodGift();
        double salesAuto = crackEggGoodSales();
        double crackGoodClosing = state.getCrackGoodOpen() + state.getCrackGoodProd() + state.getGoodClassify() - giftAuto - salesAuto;
        double crackRoughClosing = state.getCrackRoughOpen() + state.getCrackRoughProd() + state.getRoughClassify();

        return new ProductionDayStateResponse(
                date, rows,
                state.getCrackGoodOpen(), state.getCrackRoughOpen(), state.getCrackGoodProd(), state.getCrackRoughProd(),
                state.getGoodClassify(), state.getRoughClassify(),
                totalCrackFromWhole, classifyTotal, mismatch,
                giftAuto, salesAuto, crackGoodClosing, crackRoughClosing,
                state.getEnteredBy(), state.getUpdatedBy(), isEditable(date)
        );
    }

    /** See getTodayPenEntries()'s javadoc — mapped to the response DTO inside the transaction for the same reason. */
    @Transactional(readOnly = true)
    public Page<ProductionPenEntryResponse> penEntryHistory(Long penId, Pageable pageable) {
        Page<ProductionPenEntry> page;
        if (penId != null) {
            Pen pen = penRepository.findById(penId).orElseThrow(() -> new NotFoundException("Pen not found."));
            page = penEntryRepository.findAllByPenOrderByEntryDateDesc(pen, pageable);
        } else {
            page = penEntryRepository.findAllByOrderByEntryDateDesc(pageable);
        }
        return page.map(this::toPenEntryResponse);
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
            new ReportColumn("md", "Medium", true),
            new ReportColumn("sm", "Small", true),
            new ReportColumn("pl", "Pullet", true),
            new ReportColumn("wh", "White", true)
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
        LocalDate start = monthsList.get(0).atDay(1);
        LocalDate end = monthsList.get(monthsList.size() - 1).atEndOfMonth();
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
        double xl = entries.stream().mapToDouble(ProductionPenEntry::getQtyXl).sum();
        double lg = entries.stream().mapToDouble(ProductionPenEntry::getQtyLg).sum();
        double md = entries.stream().mapToDouble(ProductionPenEntry::getQtyMd).sum();
        double sm = entries.stream().mapToDouble(ProductionPenEntry::getQtySm).sum();
        double pl = entries.stream().mapToDouble(ProductionPenEntry::getQtyPl).sum();
        double wh = entries.stream().mapToDouble(ProductionPenEntry::getQtyWh).sum();
        double crates = entries.stream().mapToDouble(ProductionPenEntry::total).sum();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("period", periodLabel);
        row.put("crates", crates);
        row.put("pct", productionPercent(crates, birdClosingForPeriod));
        row.put("xL", xl);
        row.put("lg", lg);
        row.put("md", md);
        row.put("sm", sm);
        row.put("pl", pl);
        row.put("wh", wh);
        return row;
    }
}
