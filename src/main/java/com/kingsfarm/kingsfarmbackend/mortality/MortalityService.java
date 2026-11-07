package com.kingsfarm.kingsfarmbackend.mortality;

import com.kingsfarm.kingsfarmbackend.audit.Audited;
import com.kingsfarm.kingsfarmbackend.birdstock.Pen;
import com.kingsfarm.kingsfarmbackend.birdstock.PenRepository;
import com.kingsfarm.kingsfarmbackend.common.Mod;
import com.kingsfarm.kingsfarmbackend.common.PaymentMethod;
import com.kingsfarm.kingsfarmbackend.common.exception.BadRequestException;
import com.kingsfarm.kingsfarmbackend.common.exception.ForbiddenException;
import com.kingsfarm.kingsfarmbackend.common.exception.NotFoundException;
import com.kingsfarm.kingsfarmbackend.common.reports.ReportColumn;
import com.kingsfarm.kingsfarmbackend.common.reports.ReportPeriods;
import com.kingsfarm.kingsfarmbackend.common.reports.ReportTableResponse;
import com.kingsfarm.kingsfarmbackend.mortality.dto.*;
import com.kingsfarm.kingsfarmbackend.openingstock.OpeningStockLockService;
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
 * Backs MortalityView in full — five dead-bird categories (Good, Dry, Runt,
 * Green, PM/Reject), each with its own closing-stock formula (see
 * {@link MortCategoryStockRow}). Self-contained: unlike every other Phase 3
 * module, Mortality has no cross-module feed in or out (BACKEND_PLAN.md §6
 * never lists it, and it deliberately does not sync with
 * {@code BirdPenRecord.mortality} even though both are called "mortality" —
 * see {@link MortPenEntry}'s javadoc).
 */
@Service
public class MortalityService {

    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private com.kingsfarm.kingsfarmbackend.backfill.EditWindowService editWindow;

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final int GOOD_OPENING_DEFAULT = 0;
    private static final int DRY_OPENING_DEFAULT = 0;
    private static final int RUNT_OPENING_DEFAULT = 0;
    private static final int GREEN_OPENING_DEFAULT = 0;
    private static final int PM_REJECT_OPENING_DEFAULT = 0;

    private final PenRepository penRepository;
    private final MortPenEntryRepository penEntryRepository;
    private final MortCategoryValueRepository categoryValueRepository;
    private final MortSaleEntryRepository saleRepository;
    private final MortGiftLogRepository giftLogRepository;
    private final MortCatfishDisposalStateRepository catfishDisposalRepository;
    private final OpeningStockLockService lockService;

    public MortalityService(PenRepository penRepository,
                             MortPenEntryRepository penEntryRepository,
                             MortCategoryValueRepository categoryValueRepository,
                             MortSaleEntryRepository saleRepository,
                             MortGiftLogRepository giftLogRepository,
                             MortCatfishDisposalStateRepository catfishDisposalRepository,
                             OpeningStockLockService lockService) {
        this.penRepository = penRepository;
        this.penEntryRepository = penEntryRepository;
        this.categoryValueRepository = categoryValueRepository;
        this.saleRepository = saleRepository;
        this.giftLogRepository = giftLogRepository;
        this.catfishDisposalRepository = catfishDisposalRepository;
        this.lockService = lockService;
    }

    // ── Category values (opening + live gift total) ─────────────────────────

    private static int defaultOpeningFor(MortCat category) {
        return switch (category) {
            case GOOD -> GOOD_OPENING_DEFAULT;
            case DRY -> DRY_OPENING_DEFAULT;
            case RUNT -> RUNT_OPENING_DEFAULT;
            case GREEN -> GREEN_OPENING_DEFAULT;
            case PM_REJECT -> PM_REJECT_OPENING_DEFAULT;
        };
    }

    private MortCategoryValue categoryValue(MortCat category) {
        return categoryValueRepository.findByCategory(category).orElseGet(() ->
                categoryValueRepository.save(MortCategoryValue.builder().category(category).opening(defaultOpeningFor(category)).build()));
    }

    /**
     * Read-only twin of {@link #categoryValue} — same shape as
     * {@link #peekTodayCatfishDisposal()} and for the same reason: Spring
     * marks the JDBC connection itself read-only under
     * {@code @Transactional(readOnly = true)}, so categoryValue()'s "insert
     * a default row the first time this category is looked up" write throws
     * "Connection is read-only" (SQLState S1009) the moment it runs inside a
     * read-only transaction — exactly what was happening on every category
     * on GET /mortality/stock for a fresh database. Returns an unsaved,
     * default-valued instance instead of ever writing from a read path.
     */
    private MortCategoryValue peekCategoryValue(MortCat category) {
        return categoryValueRepository.findByCategory(category)
                .orElseGet(() -> MortCategoryValue.builder().category(category).opening(defaultOpeningFor(category)).build());
    }

    @Audited(module = Mod.MORTALITY, action = "Update Opening Stock", detail = "#category + ' -> ' + #request.value()")
    @Transactional
    public void setOpening(MortCat category, UpdateOpeningRequest request) {
        if (lockService.isLocked(Mod.MORTALITY, category.wire())) {
            throw new ForbiddenException("Opening Stock is locked. Submit an Opening Stock request for the Administrator to approve.");
        }
        MortCategoryValue value = categoryValue(category);
        LocalDate day = request.date();
        if (day != null && day.isBefore(LocalDate.now())) {
            if (!isEditable(day)) {
                throw new ForbiddenException("This day isn't open for entry. Days must be entered in order - the banner at the top shows the day to work on. A day that is already closed needs the Administrator's permission.");
            }
            // The live opening is today's; make it whatever gives the typed opening on that day.
            int live = request.value();
            for (LocalDate d = day; d.isBefore(LocalDate.now()); d = d.plusDays(1)) {
                live += netOn(category, d);
            }
            if (live < 0) {
                throw new BadRequestException("That opening would make a later day's stock negative.");
            }
            value.setOpening(live);
        } else {
            value.setOpening(request.value());
        }
        categoryValueRepository.save(value);
    }

    @Transactional
    public void lockOpening(MortCat category) {
        lockService.lock(Mod.MORTALITY, category.wire());
    }

    // ── Stock overview ───────────────────────────────────────────────────────

    /** Sum of today's pen entries for a category — mirrors ProductionService.catProdTotals. */
    @Transactional(readOnly = true)
    public int producedToday(MortCat category) {
        return producedOn(category, LocalDate.now());
    }

    private int producedOn(MortCat category, LocalDate day) {
        return penEntryRepository.findAllByEntryDate(day).stream()
                .mapToInt(e -> e.get(category))
                .sum();
    }

    /** Daily rollover — each category's finished-day closing becomes the new (locked) opening; gifts restart at zero. */
    @Transactional
    public void rollOver(LocalDate prevDay) {
        MortCatfishDisposalState cd = catfishDisposalRepository.findByEntryDate(prevDay)
                .orElseGet(() -> MortCatfishDisposalState.builder().entryDate(prevDay).build());
        for (MortCat category : MortCat.values()) {
            MortCategoryValue value = categoryValue(category);
            int produced = producedOn(category, prevDay);
            int sales = category.saleable()
                    ? saleRepository.sumQtyByCategoryBetween(category, com.kingsfarm.kingsfarmbackend.common.AppDay.start(prevDay), com.kingsfarm.kingsfarmbackend.common.AppDay.end(prevDay))
                    : 0;
            int gift = category.saleable() ? value.getGiftQty() : 0;
            int closing = switch (category) {
                case GREEN -> value.getOpening() + produced - cd.getCatfishQty();
                case PM_REJECT -> value.getOpening() + produced - cd.getDisposalQty();
                default -> value.getOpening() + produced - sales - gift;
            };
            value.setOpening(closing);
            value.setGiftQty(0);
            categoryValueRepository.save(value);
            if (!editWindow.isCatchingUp(Mod.MORTALITY)) lockService.lock(Mod.MORTALITY, category.wire());
        }
    }

    @Transactional(readOnly = true)
    public List<MortCategoryStockRow> stockOverview() {
        return List.of(MortCat.values()).stream().map(this::stockRow).toList();
    }

    /** What changed a category's stock on one finished day (gifts for past days are not tracked separately). */
    private int netOn(MortCat category, LocalDate day) {
        int produced = producedOn(category, day);
        MortCatfishDisposalState cd = catfishDisposalRepository.findByEntryDate(day).orElse(null);
        return switch (category) {
            case GREEN -> produced - (cd == null ? 0 : cd.getCatfishQty());
            case PM_REJECT -> produced - (cd == null ? 0 : cd.getDisposalQty());
            default -> produced - saleRepository.sumQtyByCategoryBetween(category,
                    com.kingsfarm.kingsfarmbackend.common.AppDay.start(day), com.kingsfarm.kingsfarmbackend.common.AppDay.end(day));
        };
    }

    /** Opening of a finished day = today's live opening minus everything that changed stock from that day up to yesterday. */
    private int openingOn(MortCat category, LocalDate day, int liveOpening) {
        int opening = liveOpening;
        for (LocalDate d = day; d.isBefore(LocalDate.now()); d = d.plusDays(1)) {
            opening -= netOn(category, d);
        }
        return opening;
    }

    /** Stock overview for the day being worked on: today = live figures; a finished day = that day's own opening, produced, sales and closing. */
    @Transactional(readOnly = true)
    public List<MortCategoryStockRow> stockOverview(LocalDate date) {
        if (date == null || !date.isBefore(LocalDate.now())) {
            return stockOverview();
        }
        MortCatfishDisposalState cd = catfishDisposalRepository.findByEntryDate(date).orElse(null);
        List<MortCategoryStockRow> rows = new java.util.ArrayList<>();
        for (MortCat category : MortCat.values()) {
            MortCategoryValue value = peekCategoryValue(category);
            int opening = openingOn(category, date, value.getOpening());
            int produced = producedOn(category, date);
            int sales = category.saleable()
                    ? saleRepository.sumQtyByCategoryBetween(category, com.kingsfarm.kingsfarmbackend.common.AppDay.start(date), com.kingsfarm.kingsfarmbackend.common.AppDay.end(date))
                    : 0;
            int catfish = category == MortCat.GREEN && cd != null ? cd.getCatfishQty() : 0;
            int disposal = category == MortCat.PM_REJECT && cd != null ? cd.getDisposalQty() : 0;
            int closing = opening + produced - sales - catfish - disposal;
            rows.add(new MortCategoryStockRow(category, opening, lockService.isLocked(Mod.MORTALITY, category.wire()),
                    produced, sales, 0, catfish, disposal, closing));
        }
        return rows;
    }

    private MortCategoryStockRow stockRow(MortCat category) {
        MortCategoryValue value = peekCategoryValue(category);
        int produced = producedToday(category);
        int sales = category.saleable()
                ? saleRepository.sumQtyByCategoryBetween(category, com.kingsfarm.kingsfarmbackend.common.AppDay.start(LocalDate.now()), com.kingsfarm.kingsfarmbackend.common.AppDay.end(LocalDate.now()))
                : 0;
        int gift = category.saleable() ? value.getGiftQty() : 0;
        MortCatfishDisposalState cd = peekTodayCatfishDisposal();
        int catfish = category == MortCat.GREEN ? cd.getCatfishQty() : 0;
        int disposal = category == MortCat.PM_REJECT ? cd.getDisposalQty() : 0;

        int closing = switch (category) {
            case GREEN -> value.getOpening() + produced - catfish;
            case PM_REJECT -> value.getOpening() + produced - disposal;
            default -> value.getOpening() + produced - sales - gift;
        };

        return new MortCategoryStockRow(
                category, value.getOpening(), lockService.isLocked(Mod.MORTALITY, category.wire()),
                produced, sales, gift, catfish, disposal, closing
        );
    }

    private int closingFor(MortCat category) {
        return stockRow(category).closing();
    }

    // ── Pen mortality entry ──────────────────────────────────────────────────

    /**
     * Returns the response DTO directly, resolved inside this transaction —
     * MortPenEntry.pen is a LAZY @ManyToOne, and MortPenEntryResponse.from()
     * dereferences pen.getName(); with open-in-view disabled, mapping this
     * in the controller after the transaction closes throws
     * LazyInitializationException (same bug class fixed in
     * OpeningStockRequestService — see its javadoc).
     */
    @Transactional
    public List<MortPenEntryResponse> getTodayPenEntries() {
        LocalDate today = LocalDate.now();
        return penRepository.findAllByActiveTrueOrderByNameAsc().stream()
                .map(pen -> findOrCreateTodayEntry(pen, today))
                .map(e -> MortPenEntryResponse.from(e, isEditable(e.getEntryDate())))
                .toList();
    }

    /** The day an entry is for: null = today; future refused; a past day must be inside the grace window or approved. */
    private LocalDate resolveEntryDay(LocalDate requested) {
        LocalDate today = LocalDate.now();
        LocalDate day = requested == null ? today : requested;
        if (day.isAfter(today)) {
            throw new BadRequestException("You can't enter records for a future date.");
        }
        if (!day.isEqual(today) && !editWindow.isOpen(Mod.MORTALITY, day)) {
            throw new ForbiddenException("This day isn't open for entry. Days must be entered in order - the banner at the top shows the day to work on. A day that is already closed needs the Administrator's permission.");
        }
        return day;
    }

    /**
     * A finished day's closing stock for this category changed by {@code delta}, so the live
     * opening (which every later day builds on) moves by the same amount. System
     * adjustment — not a manager edit, so it ignores the opening-stock lock.
     */
    private void carryDelta(MortCat category, int delta) {
        if (delta == 0) {
            return;
        }
        MortCategoryValue value = categoryValue(category);
        if (value.getOpening() + delta < 0) {
            throw new BadRequestException("This change would make " + category + " stock negative on a later day.");
        }
        value.setOpening(value.getOpening() + delta);
        categoryValueRepository.save(value);
    }

    /** Pen entries for any day; a past day only creates missing rows when the caller may edit it. */
    @Transactional
    public List<MortPenEntryResponse> getPenEntriesFor(LocalDate date) {
        if (date == null || date.isEqual(LocalDate.now())) {
            return getTodayPenEntries();
        }
        boolean canEdit = isEditable(date);
        return penRepository.findAllByActiveTrueOrderByNameAsc().stream()
                .map(pen -> canEdit
                        ? java.util.Optional.of(findOrCreateTodayEntry(pen, date))
                        : penEntryRepository.findByPenAndEntryDate(pen, date))
                .flatMap(java.util.Optional::stream)
                .map(e -> MortPenEntryResponse.from(e, isEditable(e.getEntryDate())))
                .toList();
    }

    private MortPenEntry findOrCreateTodayEntry(Pen pen, LocalDate today) {
        return penEntryRepository.findByPenAndEntryDate(pen, today)
                .orElseGet(() -> penEntryRepository.save(MortPenEntry.builder().pen(pen).entryDate(today).build()));
    }

    /** See getTodayPenEntries()'s javadoc — same lazy-pen mapping-inside-transaction reasoning. */
    @Audited(module = Mod.MORTALITY, action = "Save Mortality Entry", detail = "'Pen #' + #penId + ' / ' + #request.category() + ' = ' + #request.qty()")
    @Transactional
    public MortPenEntryResponse updatePenEntry(Long penId, UpdateMortPenEntryRequest request, String username) {
        Pen pen = penRepository.findById(penId).orElseThrow(() -> new NotFoundException("Pen not found."));
        LocalDate day = resolveEntryDay(request.date());
        MortPenEntry entry = findOrCreateTodayEntry(pen, day);
        if (!isEditable(entry.getEntryDate())) {
            throw new ForbiddenException("This day isn't open for entry. Days must be entered in order - the banner at the top shows the day to work on. A day that is already closed needs the Administrator's permission.");
        }
        int before = entry.get(request.category());
        entry.set(request.category(), request.qty());
        if (entry.getEnteredBy() == null) {
            entry.setEnteredBy(username);
        } else {
            entry.setUpdatedBy(username);
        }
        MortPenEntry saved = penEntryRepository.save(entry);
        if (day.isBefore(LocalDate.now())) {
            carryDelta(request.category(), request.qty() - before);
        }
        return MortPenEntryResponse.from(saved, isEditable(saved.getEntryDate()));
    }

    public boolean isEditable(LocalDate entryDate) {
        return editWindow.isOpen(Mod.MORTALITY, entryDate);
    }

    // ── Sales ─────────────────────────────────────────────────────────────

    @Audited(module = Mod.MORTALITY, action = "Record Sale", type = LogType.AUDIT, detail = "'Customer: ' + #request.customer()")
    @Transactional
    public MortSaleEntry createSale(CreateMortSaleRequest request, String username) {
        if (!request.category().saleable()) {
            throw new BadRequestException("Sales are only allowed for Good, Dry, and Runt.");
        }
        validatePaymentMethods(request.paymentMethods(), request.bank());
        LocalDate day = resolveEntryDay(request.date());
        boolean past = !day.isEqual(LocalDate.now());

        int available = closingFor(request.category());
        if (request.qty() > available) {
            throw new BadRequestException("Cannot sell " + request.qty() + " — only " + available + " " + request.category() + " available.");
        }

        long cash = request.paymentMethods().contains(PaymentMethod.CASH) ? request.cashAmount() : 0;
        long transfer = request.paymentMethods().contains(PaymentMethod.TRANSFER) ? request.transferAmount() : 0;

        MortSaleEntry entry = MortSaleEntry.builder()
                .category(request.category()).customer(request.customer()).state(request.state())
                .qty(request.qty()).price(request.price())
                .paymentMethods(new HashSet<>(request.paymentMethods()))
                .bank(request.paymentMethods().contains(PaymentMethod.TRANSFER) ? request.bank() : null)
                .cashAmount(cash).transferAmount(transfer).amountPaid(cash + transfer)
                .credit(request.credit()).advance(request.advance())
                .enteredBy(username)
                .build();
        if (past) {
            entry.setOccurredAt(day.atTime(12, 0).atZone(ZONE).toInstant());
        }
        MortSaleEntry saved = saleRepository.save(entry);
        if (past) {
            carryDelta(request.category(), -request.qty());
        }
        return saved;
    }

    @Audited(module = Mod.MORTALITY, action = "Edit Sale", type = LogType.AUDIT, detail = "'Transaction #' + #id")
    @Transactional
    public MortSaleEntry updateSale(Long id, UpdateMortSaleRequest request, String username) {
        MortSaleEntry entry = saleRepository.findById(id).orElseThrow(() -> new NotFoundException("Transaction not found."));
        if (!isEditableInstant(entry.getOccurredAt())) {
            throw new ForbiddenException("This day isn't open for entry. Days must be entered in order - the banner at the top shows the day to work on. A day that is already closed needs the Administrator's permission.");
        }
        int oldQty = entry.getQty();
        entry.setCustomer(request.customer());
        entry.setState(request.state());
        entry.setQty(request.qty());
        entry.setPrice(request.price());
        entry.setCredit(request.credit());
        entry.setAdvance(request.advance());
        entry.setUpdatedBy(username);
        MortSaleEntry saved = saleRepository.save(entry);
        if (saved.getOccurredAt().atZone(ZONE).toLocalDate().isBefore(LocalDate.now())) {
            carryDelta(saved.getCategory(), oldQty - request.qty());
        }
        return saved;
    }

    @org.springframework.beans.factory.annotation.Value("${app.transactions.delete-enabled:false}")
    private boolean saleDeleteEnabled;

    public boolean isSaleDeleteEnabled() {
        return saleDeleteEnabled;
    }

    /**
     * Administrator-only removal of a mistaken sale (switched by TRANSACTION_DELETE_ENABLED). For today the
     * stock simply recalculates; for an earlier day the birds go back into that day's closing, so every later
     * day's opening is raised by the same amount.
     */
    @Audited(module = Mod.MORTALITY, action = "Delete Sale", type = LogType.AUDIT, detail = "'Transaction #' + #id")
    @Transactional
    public void deleteSale(Long id) {
        if (!saleDeleteEnabled) {
            throw new ForbiddenException("Deleting transactions is switched off.");
        }
        MortSaleEntry entry = saleRepository.findById(id).orElseThrow(() -> new NotFoundException("Transaction not found."));
        LocalDate day = entry.getOccurredAt().atZone(ZONE).toLocalDate();
        MortCat category = entry.getCategory();
        int qty = entry.getQty();
        saleRepository.delete(entry);
        saleRepository.flush();
        if (day.isBefore(LocalDate.now())) {
            carryDelta(category, qty);
        }
    }

    @Transactional(readOnly = true)
    public Page<MortSaleEntry> saleHistory(Pageable pageable) {
        return saleRepository.findAllByOrderByOccurredAtDesc(pageable);
    }

    // ── Gift ──────────────────────────────────────────────────────────────

    /** One atomic action: overwrites each saleable category's live gift total AND appends a permanent log row — matches saveGifts() exactly (see SaveGiftRequest javadoc). */
    @Audited(module = Mod.MORTALITY, action = "Save Gift", type = LogType.AUDIT, detail = "'Recipient: ' + #request.recipient()")
    @Transactional
    public MortGiftLogEntry saveGift(SaveGiftRequest request, String username) {
        if (request.good() > closingFor(MortCat.GOOD)
                || request.dry() > closingFor(MortCat.DRY)
                || request.runt() > closingFor(MortCat.RUNT)) {
            throw new BadRequestException("Gift quantity exceeds available stock.");
        }
        setGiftQty(MortCat.GOOD, request.good());
        setGiftQty(MortCat.DRY, request.dry());
        setGiftQty(MortCat.RUNT, request.runt());

        MortGiftLogEntry entry = MortGiftLogEntry.builder()
                .good(request.good()).dry(request.dry()).runt(request.runt())
                .recipient(request.recipient()).authorizer(request.authorizer())
                .enteredBy(username)
                .build();
        return giftLogRepository.save(entry);
    }

    private void setGiftQty(MortCat category, int qty) {
        MortCategoryValue value = categoryValue(category);
        value.setGiftQty(qty);
        categoryValueRepository.save(value);
    }

    @Audited(module = Mod.MORTALITY, action = "Edit Gift Log Entry", type = LogType.AUDIT, detail = "'Entry #' + #id")
    @Transactional
    public MortGiftLogEntry updateGiftEntry(Long id, UpdateGiftLogEntryRequest request, String username) {
        MortGiftLogEntry entry = giftLogRepository.findById(id).orElseThrow(() -> new NotFoundException("Gift log entry not found."));
        if (!isEditableInstant(entry.getOccurredAt())) {
            throw new ForbiddenException("This day isn't open for entry. Days must be entered in order - the banner at the top shows the day to work on. A day that is already closed needs the Administrator's permission.");
        }
        entry.setGood(request.good());
        entry.setDry(request.dry());
        entry.setRunt(request.runt());
        entry.setRecipient(request.recipient());
        entry.setAuthorizer(request.authorizer());
        entry.setUpdatedBy(username);
        return giftLogRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public Page<MortGiftLogEntry> giftLogHistory(Pageable pageable) {
        return giftLogRepository.findAllByOrderByOccurredAtDesc(pageable);
    }

    // ── Catfish & Disposal ────────────────────────────────────────────────

    /** Catfish/disposal row for any day; a past day is created only when the caller may edit it. */
    @Transactional
    public MortCatfishDisposalState catfishDisposalFor(LocalDate date) {
        if (date == null || date.isEqual(LocalDate.now())) {
            return todayCatfishDisposal();
        }
        if (isEditable(date)) {
            return catfishDisposalRepository.findByEntryDate(date)
                    .orElseGet(() -> catfishDisposalRepository.save(MortCatfishDisposalState.builder().entryDate(date).build()));
        }
        return catfishDisposalRepository.findByEntryDate(date)
                .orElseGet(() -> MortCatfishDisposalState.builder().entryDate(date).build());
    }

    @Transactional
    public MortCatfishDisposalState todayCatfishDisposal() {
        LocalDate today = LocalDate.now();
        return catfishDisposalRepository.findByEntryDate(today)
                .orElseGet(() -> catfishDisposalRepository.save(MortCatfishDisposalState.builder().entryDate(today).build()));
    }

    /**
     * Read-only variant for use inside {@link #stockRow} (itself called from
     * the read-only {@link #stockOverview}) — self-invocation within this
     * class bypasses the {@code @Transactional} proxy entirely, so calling
     * the persisting {@link #todayCatfishDisposal()} here would silently run
     * its insert inside a read-only transaction. Returns an unsaved,
     * zero-valued instance when no row exists yet rather than ever writing
     * from a read path.
     */
    private MortCatfishDisposalState peekTodayCatfishDisposal() {
        LocalDate today = LocalDate.now();
        return catfishDisposalRepository.findByEntryDate(today)
                .orElseGet(() -> MortCatfishDisposalState.builder().entryDate(today).build());
    }

    @Audited(module = Mod.MORTALITY, action = "Save Catfish & Disposal", detail = "'Catfish: ' + #request.catfishQty() + ', Disposal: ' + #request.disposalQty()")
    @Transactional
    public MortCatfishDisposalState updateCatfishDisposal(UpdateCatfishDisposalRequest request, String username) {
        LocalDate day = resolveEntryDay(request.date());
        MortCatfishDisposalState state = catfishDisposalFor(day);
        if (!isEditable(state.getEntryDate())) {
            throw new ForbiddenException("This day isn't open for entry. Days must be entered in order - the banner at the top shows the day to work on. A day that is already closed needs the Administrator's permission.");
        }
        boolean past = day.isBefore(LocalDate.now());
        int catfishBefore = state.getCatfishQty();
        int disposalBefore = state.getDisposalQty();
        int greenAvailable = categoryValue(MortCat.GREEN).getOpening() + producedToday(MortCat.GREEN);
        int pmRejectAvailable = categoryValue(MortCat.PM_REJECT).getOpening() + producedToday(MortCat.PM_REJECT);
        if (!past && request.catfishQty() > greenAvailable) {
            throw new BadRequestException("Catfish feed cannot exceed available Green stock (" + greenAvailable + ").");
        }
        if (!past && request.disposalQty() > pmRejectAvailable) {
            throw new BadRequestException("Disposal cannot exceed available PM/Reject stock (" + pmRejectAvailable + ").");
        }
        state.setCatfishQty(request.catfishQty());
        state.setDisposalQty(request.disposalQty());
        if (state.getEnteredBy() == null) {
            state.setEnteredBy(username);
        } else {
            state.setUpdatedBy(username);
        }
        MortCatfishDisposalState saved = catfishDisposalRepository.save(state);
        if (past) {
            carryDelta(MortCat.GREEN, -(request.catfishQty() - catfishBefore));
            carryDelta(MortCat.PM_REJECT, -(request.disposalQty() - disposalBefore));
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public int greenAvailable() {
        return peekCategoryValue(MortCat.GREEN).getOpening() + producedToday(MortCat.GREEN);
    }

    @Transactional(readOnly = true)
    public int pmRejectAvailable() {
        return peekCategoryValue(MortCat.PM_REJECT).getOpening() + producedToday(MortCat.PM_REJECT);
    }

    // ── Shared ────────────────────────────────────────────────────────────

    private void validatePaymentMethods(List<PaymentMethod> methods, String bank) {
        if (methods == null || methods.isEmpty()) {
            throw new BadRequestException("Select at least one payment method.");
        }
        if (methods.contains(PaymentMethod.TRANSFER) && (bank == null || bank.isBlank())) {
            throw new BadRequestException("Please select the bank the customer transferred to.");
        }
    }

    public boolean isEditableInstant(Instant occurredAt) {
        return editWindow.isOpen(Mod.MORTALITY, occurredAt.atZone(ZONE).toLocalDate());
    }

    // ── Reports (BACKEND_PLAN.md §8) ────────────────────────────────────────

    private static final List<ReportColumn> REPORT_COLUMNS = List.of(
            new ReportColumn("total", "Total Mortality", true),
            new ReportColumn("sales", "Sales (qty)", true),
            new ReportColumn("salesRevenue", "Sales Revenue", false),
            new ReportColumn("gifts", "Gifted", true),
            new ReportColumn("catfish", "Green → Catfish", true),
            new ReportColumn("disposal", "PM/Reject → Disposal", true)
    );

    @Transactional(readOnly = true)
    public ReportTableResponse dailyReport(LocalDate start, LocalDate end) {
        Instant rangeStart = ReportPeriods.startOfDay(start);
        Instant rangeEnd = ReportPeriods.startOfNextDay(end);
        Map<LocalDate, List<MortPenEntry>> penByDate = penEntryRepository.findAllByEntryDateBetween(start, end).stream()
                .collect(Collectors.groupingBy(MortPenEntry::getEntryDate));
        Map<LocalDate, List<MortSaleEntry>> salesByDate = saleRepository.findAllByOccurredAtGreaterThanEqualAndOccurredAtLessThan(rangeStart, rangeEnd).stream()
                .collect(Collectors.groupingBy(e -> e.getOccurredAt().atZone(ZONE).toLocalDate()));
        Map<LocalDate, List<MortGiftLogEntry>> giftsByDate = giftLogRepository.findAllByOccurredAtGreaterThanEqualAndOccurredAtLessThan(rangeStart, rangeEnd).stream()
                .collect(Collectors.groupingBy(e -> e.getOccurredAt().atZone(ZONE).toLocalDate()));
        Map<LocalDate, MortCatfishDisposalState> disposalByDate = catfishDisposalRepository.findAllByEntryDateBetween(start, end).stream()
                .collect(Collectors.toMap(MortCatfishDisposalState::getEntryDate, s -> s));
        List<Map<String, Object>> rows = ReportPeriods.daysBetween(start, end).stream()
                .map(date -> reportRow(ReportPeriods.dayLabel(date), penByDate.getOrDefault(date, List.of()),
                        salesByDate.getOrDefault(date, List.of()), giftsByDate.getOrDefault(date, List.of()), disposalByDate.get(date)))
                .toList();
        return new ReportTableResponse(REPORT_COLUMNS, rows);
    }

    @Transactional(readOnly = true)
    public ReportTableResponse monthlyReport(int months) {
        List<YearMonth> monthsList = ReportPeriods.trailingMonths(months);
        LocalDate start = monthsList.get(0).atDay(1);
        LocalDate end = monthsList.get(monthsList.size() - 1).atEndOfMonth();
        Instant rangeStart = ReportPeriods.startOfDay(start);
        Instant rangeEnd = ReportPeriods.startOfNextDay(end);
        Map<YearMonth, List<MortPenEntry>> penByMonth = penEntryRepository.findAllByEntryDateBetween(start, end).stream()
                .collect(Collectors.groupingBy(e -> YearMonth.from(e.getEntryDate())));
        Map<YearMonth, List<MortSaleEntry>> salesByMonth = saleRepository.findAllByOccurredAtGreaterThanEqualAndOccurredAtLessThan(rangeStart, rangeEnd).stream()
                .collect(Collectors.groupingBy(e -> YearMonth.from(e.getOccurredAt().atZone(ZONE).toLocalDate())));
        Map<YearMonth, List<MortGiftLogEntry>> giftsByMonth = giftLogRepository.findAllByOccurredAtGreaterThanEqualAndOccurredAtLessThan(rangeStart, rangeEnd).stream()
                .collect(Collectors.groupingBy(e -> YearMonth.from(e.getOccurredAt().atZone(ZONE).toLocalDate())));
        Map<YearMonth, List<MortCatfishDisposalState>> disposalByMonth = catfishDisposalRepository.findAllByEntryDateBetween(start, end).stream()
                .collect(Collectors.groupingBy(s -> YearMonth.from(s.getEntryDate())));
        List<Map<String, Object>> rows = monthsList.stream()
                .map(month -> reportRow(ReportPeriods.monthLabel(month), penByMonth.getOrDefault(month, List.of()),
                        salesByMonth.getOrDefault(month, List.of()), giftsByMonth.getOrDefault(month, List.of()),
                        sumDisposalStates(disposalByMonth.getOrDefault(month, List.of()))))
                .toList();
        return new ReportTableResponse(REPORT_COLUMNS, rows);
    }

    /** Monthly rows fold several days' MortCatfishDisposalState rows into one summed pseudo-state. */
    private MortCatfishDisposalState sumDisposalStates(List<MortCatfishDisposalState> states) {
        int catfish = states.stream().mapToInt(MortCatfishDisposalState::getCatfishQty).sum();
        int disposal = states.stream().mapToInt(MortCatfishDisposalState::getDisposalQty).sum();
        return MortCatfishDisposalState.builder().catfishQty(catfish).disposalQty(disposal).build();
    }

    private Map<String, Object> reportRow(String periodLabel, List<MortPenEntry> penEntries, List<MortSaleEntry> sales,
                                           List<MortGiftLogEntry> gifts, MortCatfishDisposalState disposal) {
        int total = penEntries.stream().mapToInt(MortPenEntry::total).sum();
        int salesQty = sales.stream().mapToInt(MortSaleEntry::getQty).sum();
        long salesRevenue = sales.stream().mapToLong(e -> (long) e.getQty() * e.getPrice()).sum();
        int giftQty = gifts.stream().mapToInt(g -> g.getGood() + g.getDry() + g.getRunt()).sum();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("period", periodLabel);
        row.put("total", total);
        row.put("sales", salesQty);
        row.put("salesRevenue", salesRevenue);
        row.put("gifts", giftQty);
        row.put("catfish", disposal == null ? 0 : disposal.getCatfishQty());
        row.put("disposal", disposal == null ? 0 : disposal.getDisposalQty());
        return row;
    }
}
