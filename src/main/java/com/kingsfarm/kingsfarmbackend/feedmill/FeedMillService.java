package com.kingsfarm.kingsfarmbackend.feedmill;

import com.kingsfarm.kingsfarmbackend.audit.Audited;
import com.kingsfarm.kingsfarmbackend.common.Mod;
import com.kingsfarm.kingsfarmbackend.common.exception.BadRequestException;
import com.kingsfarm.kingsfarmbackend.common.exception.ConflictException;
import com.kingsfarm.kingsfarmbackend.common.exception.ForbiddenException;
import com.kingsfarm.kingsfarmbackend.common.exception.NotFoundException;
import com.kingsfarm.kingsfarmbackend.common.reports.ReportColumn;
import com.kingsfarm.kingsfarmbackend.common.reports.ReportPeriods;
import com.kingsfarm.kingsfarmbackend.common.reports.ReportTableResponse;
import com.kingsfarm.kingsfarmbackend.feedmill.dto.*;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Backs FeedMillView in full: Ingredient Inventory, Feed Production
 * (+ Formulations and their change history), and Fish Feed Collection.
 * Unlike every other Phase 3 module, Ingredient Inventory and Fish Feed
 * Stock have no day dimension at all — see {@link FeedIngredient} and
 * {@link FishFeedStock}'s javadoc. No demo data is seeded here, same as
 * Bird Stock's {@code Pen} catalog — ingredients, feed types, and
 * formulations all start empty and are populated by the Feed Mill Manager
 * through the UI, exactly like pens are.
 */
@Service
public class FeedMillService {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final double FORMULATION_TARGET_KG = 1000;
    private static final double FORMULATION_TOLERANCE_KG = 5;

    /** The three fixed fish feed types — {@link FishFeedStock} never has more or fewer rows than this, matching the frontend's fishFeeds state, which never grows or shrinks. */
    private static final Set<String> FISH_FEED_TYPES = new LinkedHashSet<>(List.of("Fish Starter", "Fish Grower", "Fish Finisher"));

    private final FeedIngredientRepository ingredientRepository;
    private final FeedTypeRepository feedTypeRepository;
    private final FeedFormulationEntryRepository formulationRepository;
    private final FeedFormulationHistoryGroupRepository historyGroupRepository;
    private final FeedFormulationHistoryItemRepository historyItemRepository;
    private final FeedProductionLogEntryRepository productionRepository;
    private final FishFeedStockRepository fishFeedStockRepository;
    private final FeedCollectionLogEntryRepository collectionRepository;
    private final OpeningStockLockService lockService;

    public FeedMillService(FeedIngredientRepository ingredientRepository,
                            FeedTypeRepository feedTypeRepository,
                            FeedFormulationEntryRepository formulationRepository,
                            FeedFormulationHistoryGroupRepository historyGroupRepository,
                            FeedFormulationHistoryItemRepository historyItemRepository,
                            FeedProductionLogEntryRepository productionRepository,
                            FishFeedStockRepository fishFeedStockRepository,
                            FeedCollectionLogEntryRepository collectionRepository,
                            OpeningStockLockService lockService) {
        this.ingredientRepository = ingredientRepository;
        this.feedTypeRepository = feedTypeRepository;
        this.formulationRepository = formulationRepository;
        this.historyGroupRepository = historyGroupRepository;
        this.historyItemRepository = historyItemRepository;
        this.productionRepository = productionRepository;
        this.fishFeedStockRepository = fishFeedStockRepository;
        this.collectionRepository = collectionRepository;
        this.lockService = lockService;
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private static double toKg(double value, String unit) {
        return "g".equalsIgnoreCase(unit) ? value / 1000.0 : value;
    }

    // ── Ingredients ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<FeedIngredient> listIngredients() {
        return ingredientRepository.findAllByOrderByNameAsc();
    }

    private FeedIngredient ingredient(String name) {
        return ingredientRepository.findByName(name).orElseThrow(() -> new NotFoundException("Ingredient not found."));
    }

    private FeedIngredient ingredient(Long id) {
        return ingredientRepository.findById(id).orElseThrow(() -> new NotFoundException("Ingredient not found."));
    }

    @Audited(module = Mod.FEED_MILL, action = "Add Ingredient", detail = "'Ingredient: ' + #request.name()")
    @Transactional
    public FeedIngredient addIngredient(CreateIngredientRequest request, String username) {
        String name = request.name().trim();
        if (name.isEmpty()) {
            throw new BadRequestException("Enter an ingredient name.");
        }
        if (ingredientRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("An ingredient with this name already exists.");
        }
        FeedIngredient ingredient = FeedIngredient.builder()
                .name(name).unit(request.unit() == null || request.unit().isBlank() ? "kg" : request.unit())
                .opening(request.opening()).min(request.min())
                .enteredBy(username)
                .build();
        return ingredientRepository.save(ingredient);
    }

    @Audited(module = Mod.FEED_MILL, action = "Update Opening Stock", detail = "'Ingredient ' + #name + ' -> ' + #request.value()")
    @Transactional
    public void setIngredientOpening(String name, UpdateQuantityRequest request) {
        if (lockService.isLocked(Mod.FEED_MILL, name)) {
            throw new ForbiddenException("Opening Stock is locked. Submit an Opening Stock request for the Administrator to approve.");
        }
        FeedIngredient ingredient = ingredient(name);
        ingredient.setOpening(request.value());
        ingredientRepository.save(ingredient);
    }

    @Transactional
    public void lockIngredientOpening(String name) {
        lockService.lock(Mod.FEED_MILL, name);
    }

    @Transactional(readOnly = true)
    public boolean isIngredientOpeningLocked(String name) {
        return lockService.isLocked(Mod.FEED_MILL, name);
    }

    @Transactional
    public void setIngredientAdded(String name, UpdateQuantityRequest request, String username) {
        FeedIngredient ingredient = ingredient(name);
        ingredient.setAdded(request.value());
        touch(ingredient, username);
        ingredientRepository.save(ingredient);
    }

    @Transactional
    public void setIngredientMin(String name, UpdateQuantityRequest request, String username) {
        FeedIngredient ingredient = ingredient(name);
        ingredient.setMin(request.value());
        touch(ingredient, username);
        ingredientRepository.save(ingredient);
    }

    @Transactional
    public void setIngredientUnit(String name, UpdateUnitRequest request, String username) {
        FeedIngredient ingredient = ingredient(name);
        ingredient.setUnit(request.unit());
        touch(ingredient, username);
        ingredientRepository.save(ingredient);
    }

    private void touch(FeedIngredient ingredient, String username) {
        if (ingredient.getEnteredBy() == null) {
            ingredient.setEnteredBy(username);
        } else {
            ingredient.setUpdatedBy(username);
        }
    }

    @Transactional(readOnly = true)
    public List<FeedIngredient> lowIngredients() {
        return listIngredients().stream().filter(FeedIngredient::low).toList();
    }

    // ── Feed types ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<FeedType> listFeedTypes() {
        return feedTypeRepository.findAllByOrderByIdAsc();
    }

    private FeedType feedType(Long id) {
        return feedTypeRepository.findById(id).orElseThrow(() -> new NotFoundException("Feed type not found."));
    }

    @Audited(module = Mod.FEED_MILL, action = "Add Feed Type", detail = "'Feed Type: ' + #request.name()")
    @Transactional
    public FeedType addFeedType(CreateFeedTypeRequest request) {
        String name = request.name().trim();
        if (name.isEmpty()) {
            throw new BadRequestException("Enter a feed type name.");
        }
        if (feedTypeRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("This feed type already exists.");
        }
        return feedTypeRepository.save(FeedType.builder().name(name).build());
    }

    // ── Formulations ──────────────────────────────────────────────────────

    private FeedFormulationEntry formulationEntry(FeedType feedType, FeedIngredient ingredient) {
        return formulationRepository.findByFeedTypeAndIngredient(feedType, ingredient)
                .orElseGet(() -> FeedFormulationEntry.builder().feedType(feedType).ingredient(ingredient).build());
    }

    /**
     * Dense, not sparse: one row per ingredient in the whole catalog (zero
     * for any ingredient with no formulation row yet for this feed type) —
     * matches the frontend's Formulations tab, which iterates over every
     * ingredient in {@code ingredients} and falls back to
     * {@code formulations[editFormType]?.[ing.name] ?? 0} rather than only
     * showing ingredients that already have a value.
     */
    @Transactional(readOnly = true)
    public FormulationResponse formulation(Long feedTypeId) {
        FeedType feedType = feedType(feedTypeId);
        List<FeedFormulationEntry> entries = formulationRepository.findAllByFeedType(feedType);
        java.util.Map<Long, FeedFormulationEntry> byIngredientId = new java.util.HashMap<>();
        for (FeedFormulationEntry e : entries) {
            byIngredientId.put(e.getIngredient().getId(), e);
        }
        List<FormulationEntryResponse> rows = listIngredients().stream()
                .map(ing -> byIngredientId.containsKey(ing.getId())
                        ? FormulationEntryResponse.from(byIngredientId.get(ing.getId()))
                        : FormulationEntryResponse.zero(ing))
                .toList();
        double totalKg = entries.stream().mapToDouble(e -> toKg(e.getQtyPerTon(), e.getIngredient().getUnit())).sum();
        totalKg = round2(totalKg);
        String status = totalKg < FORMULATION_TARGET_KG ? "under"
                : totalKg > FORMULATION_TARGET_KG + FORMULATION_TOLERANCE_KG ? "over" : "ok";
        return new FormulationResponse(feedType.getId(), feedType.getName(), rows, totalKg, FORMULATION_TARGET_KG, FORMULATION_TOLERANCE_KG, status);
    }

    /** Live edit — persists immediately, does not touch lastSavedQtyPerTon (see FeedFormulationEntry's javadoc). */
    @Transactional
    public void updateFormulationValue(Long feedTypeId, UpdateFormulationValueRequest request) {
        FeedType feedType = feedType(feedTypeId);
        FeedIngredient ingredient = ingredient(request.ingredientId());
        FeedFormulationEntry entry = formulationEntry(feedType, ingredient);
        entry.setQtyPerTon(request.qtyPerTon());
        formulationRepository.save(entry);
    }

    /** Diffs every entry's live value against its lastSavedQtyPerTon; only creates a history group if at least one changed, matching {@code saveFormulation}'s guard. */
    @Audited(module = Mod.FEED_MILL, action = "Save Formulation", type = LogType.AUDIT, detail = "'Feed Type #' + #feedTypeId")
    @Transactional
    public FeedFormulationHistoryGroup saveFormulation(Long feedTypeId, String username) {
        FeedType feedType = feedType(feedTypeId);
        List<FeedFormulationEntry> entries = formulationRepository.findAllByFeedType(feedType);
        List<FeedFormulationEntry> changed = entries.stream().filter(e -> e.getQtyPerTon() != e.getLastSavedQtyPerTon()).toList();

        FeedFormulationHistoryGroup group = null;
        if (!changed.isEmpty()) {
            group = historyGroupRepository.save(FeedFormulationHistoryGroup.builder().feedType(feedType).changedBy(username).build());
            for (FeedFormulationEntry e : changed) {
                historyItemRepository.save(FeedFormulationHistoryItem.builder()
                        .group(group).ingredient(e.getIngredient()).oldVal(e.getLastSavedQtyPerTon()).newVal(e.getQtyPerTon())
                        .build());
            }
        }
        for (FeedFormulationEntry e : entries) {
            e.setLastSavedQtyPerTon(e.getQtyPerTon());
            formulationRepository.save(e);
        }
        return group;
    }

    @Transactional(readOnly = true)
    public Page<FeedFormulationHistoryGroup> formulationHistory(Long feedTypeId, Pageable pageable) {
        return historyGroupRepository.findAllByFeedTypeOrderByOccurredAtDesc(feedType(feedTypeId), pageable);
    }

    @Transactional(readOnly = true)
    public List<FormulationHistoryItemResponse> formulationHistoryItems(FeedFormulationHistoryGroup group) {
        return historyItemRepository.findAllByGroupOrderByIdAsc(group).stream().map(FormulationHistoryItemResponse::from).toList();
    }

    // ── Feed production ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ProductionCheckResponse checkProduction(Long feedTypeId, double qtyTons) {
        FeedType feedType = feedType(feedTypeId);
        List<FeedFormulationEntry> entries = formulationRepository.findAllByFeedType(feedType);
        List<RequirementRow> rows = new ArrayList<>();
        boolean canProduce = qtyTons > 0;
        for (FeedFormulationEntry e : entries) {
            double needed = round1(e.getQtyPerTon() * qtyTons);
            double available = e.getIngredient().closing();
            boolean ok = needed <= available;
            canProduce = canProduce && ok;
            rows.add(new RequirementRow(e.getIngredient().getId(), e.getIngredient().getName(), needed, available, e.getIngredient().getUnit(), ok));
        }
        return new ProductionCheckResponse(feedType.getId(), feedType.getName(), qtyTons, qtyTons * 1000, rows, canProduce);
    }

    @Audited(module = Mod.FEED_MILL, action = "Run Production", type = LogType.AUDIT, detail = "'Feed Type #' + #request.feedTypeId() + ' = ' + #request.qtyTons() + ' tons'")
    @Transactional
    public FeedProductionLogEntry runProduction(CreateProductionRequest request, String username) {
        if (request.qtyTons() <= 0) {
            throw new BadRequestException("Quantity must be greater than zero.");
        }
        ProductionCheckResponse check = checkProduction(request.feedTypeId(), request.qtyTons());
        if (!check.canProduce()) {
            throw new BadRequestException("Insufficient ingredient stock. Resolve shortfalls before producing.");
        }
        FeedType feedType = feedType(request.feedTypeId());
        deductIngredients(feedType, request.qtyTons());
        transferToFishFeed(feedType.getName(), request.qtyTons() * 1000);

        FeedProductionLogEntry entry = FeedProductionLogEntry.builder()
                .feedType(feedType).qtyTons(request.qtyTons()).enteredBy(username)
                .build();
        return productionRepository.save(entry);
    }

    private void deductIngredients(FeedType feedType, double qtyTons) {
        for (FeedFormulationEntry e : formulationRepository.findAllByFeedType(feedType)) {
            double deduct = round1(e.getQtyPerTon() * qtyTons);
            FeedIngredient ingredient = e.getIngredient();
            ingredient.setUsed(ingredient.getUsed() + deduct);
            ingredientRepository.save(ingredient);
        }
    }

    private void transferToFishFeed(String feedTypeName, double kg) {
        if (!FISH_FEED_TYPES.contains(feedTypeName)) {
            return;
        }
        FishFeedStock stock = fishFeedStock(feedTypeName);
        stock.setAdded(stock.getAdded() + kg);
        fishFeedStockRepository.save(stock);
    }

    @Audited(module = Mod.FEED_MILL, action = "Edit Production Run", type = LogType.AUDIT, detail = "'Run #' + #id")
    @Transactional
    public FeedProductionLogEntry updateProductionEntry(Long id, UpdateProductionRequest request, String username) {
        FeedProductionLogEntry entry = productionRepository.findById(id).orElseThrow(() -> new NotFoundException("Production run not found."));
        if (!isEditable(entry.getOccurredAt())) {
            throw new ForbiddenException("This run was recorded on a previous day and can no longer be edited.");
        }
        FeedType oldType = entry.getFeedType();
        double oldQty = entry.getQtyTons();
        FeedType newType = feedType(request.feedTypeId());
        double newQty = request.qtyTons();

        // Reconcile ingredient usage: subtract the old formulation's deduction, add the new one's — same
        // ingredient-by-ingredient union approach as the frontend's updateProductionEntry, no shortfall
        // validation on edit (the frontend doesn't validate here either, only on the original run).
        List<FeedFormulationEntry> oldEntries = formulationRepository.findAllByFeedType(oldType);
        List<FeedFormulationEntry> newEntries = formulationRepository.findAllByFeedType(newType);
        java.util.Map<Long, FeedIngredient> touched = new java.util.LinkedHashMap<>();
        java.util.Map<Long, Double> oldQtyPerTon = new java.util.HashMap<>();
        java.util.Map<Long, Double> newQtyPerTon = new java.util.HashMap<>();
        for (FeedFormulationEntry e : oldEntries) {
            touched.put(e.getIngredient().getId(), e.getIngredient());
            oldQtyPerTon.put(e.getIngredient().getId(), e.getQtyPerTon());
        }
        for (FeedFormulationEntry e : newEntries) {
            touched.put(e.getIngredient().getId(), e.getIngredient());
            newQtyPerTon.put(e.getIngredient().getId(), e.getQtyPerTon());
        }
        for (FeedIngredient ingredient : touched.values()) {
            double oldDeduct = round1(oldQtyPerTon.getOrDefault(ingredient.getId(), 0.0) * oldQty);
            double newDeduct = round1(newQtyPerTon.getOrDefault(ingredient.getId(), 0.0) * newQty);
            ingredient.setUsed(ingredient.getUsed() - oldDeduct + newDeduct);
            ingredientRepository.save(ingredient);
        }

        boolean oldFish = FISH_FEED_TYPES.contains(oldType.getName());
        boolean newFish = FISH_FEED_TYPES.contains(newType.getName());
        if (oldFish) {
            FishFeedStock stock = fishFeedStock(oldType.getName());
            stock.setAdded(stock.getAdded() - oldQty * 1000);
            fishFeedStockRepository.save(stock);
        }
        if (newFish) {
            FishFeedStock stock = fishFeedStock(newType.getName());
            stock.setAdded(stock.getAdded() + newQty * 1000);
            fishFeedStockRepository.save(stock);
        }

        entry.setFeedType(newType);
        entry.setQtyTons(newQty);
        entry.setUpdatedBy(username);
        return productionRepository.save(entry);
    }

    public boolean isEditable(Instant occurredAt) {
        return occurredAt.atZone(ZONE).toLocalDate().isEqual(LocalDate.now());
    }

    @Transactional(readOnly = true)
    public Page<FeedProductionLogEntry> productionHistory(Pageable pageable) {
        return productionRepository.findAllByOrderByOccurredAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public List<ProductionSummaryRow> productionSummary() {
        return listFeedTypes().stream().map(ft -> {
            List<FeedProductionLogEntry> runs = productionRepository.findAllByFeedType(ft);
            double total = round2(runs.stream().mapToDouble(FeedProductionLogEntry::getQtyTons).sum());
            return new ProductionSummaryRow(ft.getId(), ft.getName(), total, runs.size());
        }).toList();
    }

    // ── Fish feed stock ───────────────────────────────────────────────────

    @Transactional
    public FishFeedStock fishFeedStock(String type) {
        return fishFeedStockRepository.findByType(type)
                .orElseGet(() -> fishFeedStockRepository.save(FishFeedStock.builder().type(type).build()));
    }

    @Transactional(readOnly = true)
    public List<FishFeedStock> listFishFeedStock() {
        return FISH_FEED_TYPES.stream().map(this::fishFeedStock).toList();
    }

    @Audited(module = Mod.FEED_MILL, action = "Update Opening Stock", detail = "'Fish Feed ' + #type + ' -> ' + #request.value()")
    @Transactional
    public void setFishFeedOpening(String type, UpdateQuantityRequest request) {
        String scope = "fish:" + type;
        if (lockService.isLocked(Mod.FEED_MILL, scope)) {
            throw new ForbiddenException("Opening Stock is locked. Submit an Opening Stock request for the Administrator to approve.");
        }
        FishFeedStock stock = fishFeedStock(type);
        stock.setOpening(request.value());
        fishFeedStockRepository.save(stock);
    }

    @Transactional
    public void lockFishFeedOpening(String type) {
        lockService.lock(Mod.FEED_MILL, "fish:" + type);
    }

    @Transactional(readOnly = true)
    public boolean isFishFeedOpeningLocked(String type) {
        return lockService.isLocked(Mod.FEED_MILL, "fish:" + type);
    }

    // ── Fish feed collection ──────────────────────────────────────────────

    @Audited(module = Mod.FEED_MILL, action = "Record Fish Feed Collection", type = LogType.AUDIT, detail = "'Collected by: ' + #request.collectedBy()")
    @Transactional
    public FeedCollectionLogEntry submitCollection(CreateCollectionRequest request, String username) {
        double s = request.fishStarterKg();
        double g = request.fishGrowerKg();
        double f = request.fishFinisherKg();
        if (s + g + f == 0) {
            throw new BadRequestException("Enter at least one quantity.");
        }
        FishFeedStock starter = fishFeedStock("Fish Starter");
        FishFeedStock grower = fishFeedStock("Fish Grower");
        FishFeedStock finisher = fishFeedStock("Fish Finisher");
        if (s > starter.closing()) {
            throw new BadRequestException("Fish Starter: only " + starter.closing() + " kg available.");
        }
        if (g > grower.closing()) {
            throw new BadRequestException("Fish Grower: only " + grower.closing() + " kg available.");
        }
        if (f > finisher.closing()) {
            throw new BadRequestException("Fish Finisher: only " + finisher.closing() + " kg available.");
        }
        starter.setCollected(starter.getCollected() + s);
        grower.setCollected(grower.getCollected() + g);
        finisher.setCollected(finisher.getCollected() + f);
        fishFeedStockRepository.save(starter);
        fishFeedStockRepository.save(grower);
        fishFeedStockRepository.save(finisher);

        FeedCollectionLogEntry entry = FeedCollectionLogEntry.builder()
                .collectedBy(request.collectedBy()).fishStarterKg(s).fishGrowerKg(g).fishFinisherKg(f)
                .enteredBy(username)
                .build();
        return collectionRepository.save(entry);
    }

    /** No stock-availability validation on edit — mirrors the frontend's updateCollectionEntry exactly (only the original submitCollection validates). */
    @Audited(module = Mod.FEED_MILL, action = "Edit Fish Feed Collection", type = LogType.AUDIT, detail = "'Entry #' + #id")
    @Transactional
    public FeedCollectionLogEntry updateCollectionEntry(Long id, UpdateCollectionRequest request, String username) {
        FeedCollectionLogEntry entry = collectionRepository.findById(id).orElseThrow(() -> new NotFoundException("Collection record not found."));
        if (!isEditable(entry.getOccurredAt())) {
            throw new ForbiddenException("This record was saved on a previous day and can no longer be edited.");
        }
        FishFeedStock starter = fishFeedStock("Fish Starter");
        FishFeedStock grower = fishFeedStock("Fish Grower");
        FishFeedStock finisher = fishFeedStock("Fish Finisher");
        starter.setCollected(starter.getCollected() - entry.getFishStarterKg() + request.fishStarterKg());
        grower.setCollected(grower.getCollected() - entry.getFishGrowerKg() + request.fishGrowerKg());
        finisher.setCollected(finisher.getCollected() - entry.getFishFinisherKg() + request.fishFinisherKg());
        fishFeedStockRepository.save(starter);
        fishFeedStockRepository.save(grower);
        fishFeedStockRepository.save(finisher);

        entry.setCollectedBy(request.collectedBy());
        entry.setFishStarterKg(request.fishStarterKg());
        entry.setFishGrowerKg(request.fishGrowerKg());
        entry.setFishFinisherKg(request.fishFinisherKg());
        entry.setUpdatedBy(username);
        return collectionRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public Page<FeedCollectionLogEntry> collectionHistory(Pageable pageable) {
        return collectionRepository.findAllByOrderByOccurredAtDesc(pageable);
    }

    // ── Reports (BACKEND_PLAN.md §8) ────────────────────────────────────────
    // "alerts" (low-stock ingredients) is deliberately not a column here —
    // FeedIngredient.closing()/low() are live-only, no historical snapshot to
    // sum over a range (see §8's gap note). Current low-stock ingredients
    // remain available via GET /feed-mill/ingredients (lowIngredients()).

    private static final List<ReportColumn> REPORT_COLUMNS = List.of(
            new ReportColumn("produced", "Produced (tons)", true),
            new ReportColumn("ingrUsed", "Ingredients Used (kg)", true),
            new ReportColumn("fishCollected", "Fish Feed Collected (kg)", true)
    );

    @Transactional(readOnly = true)
    public ReportTableResponse dailyReport(LocalDate start, LocalDate end) {
        Instant rangeStart = ReportPeriods.startOfDay(start);
        Instant rangeEnd = ReportPeriods.startOfNextDay(end);
        Map<Long, Double> formulationRatioCache = new HashMap<>();
        Map<LocalDate, List<FeedProductionLogEntry>> productionByDate = productionRepository
                .findAllByOccurredAtGreaterThanEqualAndOccurredAtLessThan(rangeStart, rangeEnd).stream()
                .collect(Collectors.groupingBy(e -> e.getOccurredAt().atZone(ZONE).toLocalDate()));
        Map<LocalDate, List<FeedCollectionLogEntry>> collectionByDate = collectionRepository
                .findAllByOccurredAtGreaterThanEqualAndOccurredAtLessThan(rangeStart, rangeEnd).stream()
                .collect(Collectors.groupingBy(e -> e.getOccurredAt().atZone(ZONE).toLocalDate()));
        List<Map<String, Object>> rows = ReportPeriods.daysBetween(start, end).stream()
                .map(date -> reportRow(ReportPeriods.dayLabel(date), productionByDate.getOrDefault(date, List.of()),
                        collectionByDate.getOrDefault(date, List.of()), formulationRatioCache))
                .toList();
        return new ReportTableResponse(REPORT_COLUMNS, rows);
    }

    @Transactional(readOnly = true)
    public ReportTableResponse monthlyReport(int months) {
        List<YearMonth> monthsList = ReportPeriods.trailingMonths(months);
        LocalDate start = monthsList.getFirst().atDay(1);
        LocalDate end = monthsList.getLast().atEndOfMonth();
        Instant rangeStart = ReportPeriods.startOfDay(start);
        Instant rangeEnd = ReportPeriods.startOfNextDay(end);
        Map<Long, Double> formulationRatioCache = new HashMap<>();
        Map<YearMonth, List<FeedProductionLogEntry>> productionByMonth = productionRepository
                .findAllByOccurredAtGreaterThanEqualAndOccurredAtLessThan(rangeStart, rangeEnd).stream()
                .collect(Collectors.groupingBy(e -> YearMonth.from(e.getOccurredAt().atZone(ZONE).toLocalDate())));
        Map<YearMonth, List<FeedCollectionLogEntry>> collectionByMonth = collectionRepository
                .findAllByOccurredAtGreaterThanEqualAndOccurredAtLessThan(rangeStart, rangeEnd).stream()
                .collect(Collectors.groupingBy(e -> YearMonth.from(e.getOccurredAt().atZone(ZONE).toLocalDate())));
        List<Map<String, Object>> rows = monthsList.stream()
                .map(month -> reportRow(ReportPeriods.monthLabel(month), productionByMonth.getOrDefault(month, List.of()),
                        collectionByMonth.getOrDefault(month, List.of()), formulationRatioCache))
                .toList();
        return new ReportTableResponse(REPORT_COLUMNS, rows);
    }

    /** Sum of qtyPerTon across every ingredient in a feed type's current formulation — cached per feed type within one report call. */
    private double totalQtyPerTonFor(FeedType feedType, Map<Long, Double> cache) {
        return cache.computeIfAbsent(feedType.getId(),
                id -> formulationRepository.findAllByFeedType(feedType).stream().mapToDouble(FeedFormulationEntry::getQtyPerTon).sum());
    }

    private Map<String, Object> reportRow(String periodLabel, List<FeedProductionLogEntry> production,
                                           List<FeedCollectionLogEntry> collections, Map<Long, Double> formulationRatioCache) {
        double produced = production.stream().mapToDouble(FeedProductionLogEntry::getQtyTons).sum();
        // Ingredient usage is derived, not logged directly: each run's qtyTons × that feed type's
        // current formulation ratio (kg per ton), replaying the same math runProduction() applies
        // live — there's no per-day ingredient-usage log, only FeedIngredient's live cumulative "used".
        double ingrUsed = production.stream()
                .mapToDouble(e -> e.getQtyTons() * totalQtyPerTonFor(e.getFeedType(), formulationRatioCache))
                .sum();
        double fishCollected = collections.stream().mapToDouble(FeedCollectionLogEntry::total).sum();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("period", periodLabel);
        row.put("produced", round2(produced));
        row.put("ingrUsed", round2(ingrUsed));
        row.put("fishCollected", round2(fishCollected));
        return row;
    }
}
