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

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final int GOOD_OPENING_DEFAULT = 12;
    private static final int DRY_OPENING_DEFAULT = 8;
    private static final int RUNT_OPENING_DEFAULT = 5;
    private static final int GREEN_OPENING_DEFAULT = 3;
    private static final int PM_REJECT_OPENING_DEFAULT = 2;

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

    private MortCategoryValue categoryValue(MortCat category) {
        return categoryValueRepository.findByCategory(category).orElseGet(() -> {
            int defaultOpening = switch (category) {
                case GOOD -> GOOD_OPENING_DEFAULT;
                case DRY -> DRY_OPENING_DEFAULT;
                case RUNT -> RUNT_OPENING_DEFAULT;
                case GREEN -> GREEN_OPENING_DEFAULT;
                case PM_REJECT -> PM_REJECT_OPENING_DEFAULT;
            };
            return categoryValueRepository.save(MortCategoryValue.builder().category(category).opening(defaultOpening).build());
        });
    }

    @Audited(module = Mod.MORTALITY, action = "Update Opening Stock", detail = "#category + ' -> ' + #request.value()")
    @Transactional
    public void setOpening(MortCat category, UpdateOpeningRequest request) {
        if (lockService.isLocked(Mod.MORTALITY, category.wire())) {
            throw new ForbiddenException("Opening Stock is locked. Submit an Opening Stock request for the Administrator to approve.");
        }
        MortCategoryValue value = categoryValue(category);
        value.setOpening(request.value());
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
        return penEntryRepository.findAllByEntryDate(LocalDate.now()).stream()
                .mapToInt(e -> e.get(category))
                .sum();
    }

    @Transactional(readOnly = true)
    public List<MortCategoryStockRow> stockOverview() {
        return List.of(MortCat.values()).stream().map(this::stockRow).toList();
    }

    private MortCategoryStockRow stockRow(MortCat category) {
        MortCategoryValue value = categoryValue(category);
        int produced = producedToday(category);
        int sales = category.saleable() ? saleRepository.sumQtyByCategory(category) : 0;
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

    @Transactional
    public List<MortPenEntry> getTodayPenEntries() {
        LocalDate today = LocalDate.now();
        return penRepository.findAllByActiveTrueOrderByNameAsc().stream()
                .map(pen -> findOrCreateTodayEntry(pen, today))
                .toList();
    }

    private MortPenEntry findOrCreateTodayEntry(Pen pen, LocalDate today) {
        return penEntryRepository.findByPenAndEntryDate(pen, today)
                .orElseGet(() -> penEntryRepository.save(MortPenEntry.builder().pen(pen).entryDate(today).build()));
    }

    @Audited(module = Mod.MORTALITY, action = "Save Mortality Entry", detail = "'Pen #' + #penId + ' / ' + #request.category() + ' = ' + #request.qty()")
    @Transactional
    public MortPenEntry updatePenEntry(Long penId, UpdateMortPenEntryRequest request, String username) {
        Pen pen = penRepository.findById(penId).orElseThrow(() -> new NotFoundException("Pen not found."));
        LocalDate today = LocalDate.now();
        MortPenEntry entry = findOrCreateTodayEntry(pen, today);
        if (!isEditable(entry.getEntryDate())) {
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

    public boolean isEditable(LocalDate entryDate) {
        return entryDate.isEqual(LocalDate.now());
    }

    // ── Sales ─────────────────────────────────────────────────────────────

    @Audited(module = Mod.MORTALITY, action = "Record Sale", type = LogType.AUDIT, detail = "'Customer: ' + #request.customer()")
    @Transactional
    public MortSaleEntry createSale(CreateMortSaleRequest request, String username) {
        if (!request.category().saleable()) {
            throw new BadRequestException("Sales are only allowed for Good, Dry, and Runt.");
        }
        validatePaymentMethods(request.paymentMethods(), request.bank());

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
        return saleRepository.save(entry);
    }

    @Audited(module = Mod.MORTALITY, action = "Edit Sale", type = LogType.AUDIT, detail = "'Transaction #' + #id")
    @Transactional
    public MortSaleEntry updateSale(Long id, UpdateMortSaleRequest request, String username) {
        MortSaleEntry entry = saleRepository.findById(id).orElseThrow(() -> new NotFoundException("Transaction not found."));
        if (!isEditableInstant(entry.getOccurredAt())) {
            throw new ForbiddenException("This transaction was recorded on a previous day and can no longer be edited.");
        }
        entry.setCustomer(request.customer());
        entry.setState(request.state());
        entry.setQty(request.qty());
        entry.setPrice(request.price());
        entry.setCredit(request.credit());
        entry.setAdvance(request.advance());
        entry.setUpdatedBy(username);
        return saleRepository.save(entry);
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
            throw new ForbiddenException("This record was saved on a previous day and can no longer be edited.");
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
        MortCatfishDisposalState state = todayCatfishDisposal();
        if (!isEditable(state.getEntryDate())) {
            throw new ForbiddenException("This record was saved on a previous day and can no longer be edited.");
        }
        int greenAvailable = categoryValue(MortCat.GREEN).getOpening() + producedToday(MortCat.GREEN);
        int pmRejectAvailable = categoryValue(MortCat.PM_REJECT).getOpening() + producedToday(MortCat.PM_REJECT);
        if (request.catfishQty() > greenAvailable) {
            throw new BadRequestException("Catfish feed cannot exceed available Green stock (" + greenAvailable + ").");
        }
        if (request.disposalQty() > pmRejectAvailable) {
            throw new BadRequestException("Disposal cannot exceed available PM/Reject stock (" + pmRejectAvailable + ").");
        }
        state.setCatfishQty(request.catfishQty());
        state.setDisposalQty(request.disposalQty());
        if (state.getEnteredBy() == null) {
            state.setEnteredBy(username);
        } else {
            state.setUpdatedBy(username);
        }
        return catfishDisposalRepository.save(state);
    }

    @Transactional(readOnly = true)
    public int greenAvailable() {
        return categoryValue(MortCat.GREEN).getOpening() + producedToday(MortCat.GREEN);
    }

    @Transactional(readOnly = true)
    public int pmRejectAvailable() {
        return categoryValue(MortCat.PM_REJECT).getOpening() + producedToday(MortCat.PM_REJECT);
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
        return occurredAt.atZone(ZONE).toLocalDate().isEqual(LocalDate.now());
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
