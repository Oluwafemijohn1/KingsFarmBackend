package com.kingsfarm.kingsfarmbackend.feedmill;

import com.kingsfarm.kingsfarmbackend.common.PageResponse;
import com.kingsfarm.kingsfarmbackend.feedmill.dto.*;
import com.kingsfarm.kingsfarmbackend.security.AuthenticatedPrincipal;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Same role split as every other module: Administrator + Feed Mill Manager read, only Feed Mill Manager writes. */
@RestController
@RequestMapping("/api/v1/feed-mill")
@PreAuthorize("hasAnyRole('ADMINISTRATOR', 'FEED_MILL_MANAGER')")
public class FeedMillController {

    private final FeedMillService service;

    public FeedMillController(FeedMillService service) {
        this.service = service;
    }

    // ── Ingredients ───────────────────────────────────────────────────────

    @GetMapping("/ingredients")
    public List<FeedIngredientResponse> listIngredients() {
        return service.listIngredients().stream()
                .map(i -> FeedIngredientResponse.from(i, service.isIngredientOpeningLocked(i.getName())))
                .toList();
    }

    @PostMapping("/ingredients")
    @PreAuthorize("hasRole('FEED_MILL_MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public FeedIngredientResponse addIngredient(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                                 @Valid @RequestBody CreateIngredientRequest request) {
        FeedIngredient ingredient = service.addIngredient(request, principal.username());
        return FeedIngredientResponse.from(ingredient, service.isIngredientOpeningLocked(ingredient.getName()));
    }

    @PatchMapping("/ingredients/{name}/opening")
    @PreAuthorize("hasRole('FEED_MILL_MANAGER')")
    public void setIngredientOpening(@PathVariable String name, @Valid @RequestBody UpdateQuantityRequest request) {
        service.setIngredientOpening(name, request);
    }

    @PostMapping("/ingredients/{name}/opening/lock")
    @PreAuthorize("hasRole('FEED_MILL_MANAGER')")
    public void lockIngredientOpening(@PathVariable String name) {
        service.lockIngredientOpening(name);
    }

    @PatchMapping("/ingredients/{name}/added")
    @PreAuthorize("hasRole('FEED_MILL_MANAGER')")
    public void setIngredientAdded(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                    @PathVariable String name, @Valid @RequestBody UpdateQuantityRequest request) {
        service.setIngredientAdded(name, request, principal.username());
    }

    @PatchMapping("/ingredients/{name}/min")
    @PreAuthorize("hasRole('FEED_MILL_MANAGER')")
    public void setIngredientMin(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                  @PathVariable String name, @Valid @RequestBody UpdateQuantityRequest request) {
        service.setIngredientMin(name, request, principal.username());
    }

    @PatchMapping("/ingredients/{name}/unit")
    @PreAuthorize("hasRole('FEED_MILL_MANAGER')")
    public void setIngredientUnit(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                   @PathVariable String name, @Valid @RequestBody UpdateUnitRequest request) {
        service.setIngredientUnit(name, request, principal.username());
    }

    // ── Feed types ────────────────────────────────────────────────────────

    @GetMapping("/feed-types")
    public List<FeedTypeResponse> listFeedTypes() {
        return service.listFeedTypes().stream().map(FeedTypeResponse::from).toList();
    }

    @PostMapping("/feed-types")
    @PreAuthorize("hasRole('FEED_MILL_MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public FeedTypeResponse addFeedType(@Valid @RequestBody CreateFeedTypeRequest request) {
        return FeedTypeResponse.from(service.addFeedType(request));
    }

    // ── Formulations ──────────────────────────────────────────────────────

    @GetMapping("/feed-types/{feedTypeId}/formulation")
    public FormulationResponse formulation(@PathVariable Long feedTypeId) {
        return service.formulation(feedTypeId);
    }

    @PatchMapping("/feed-types/{feedTypeId}/formulation")
    @PreAuthorize("hasRole('FEED_MILL_MANAGER')")
    public void updateFormulationValue(@PathVariable Long feedTypeId, @Valid @RequestBody UpdateFormulationValueRequest request) {
        service.updateFormulationValue(feedTypeId, request);
    }

    @PostMapping("/feed-types/{feedTypeId}/formulation/save")
    @PreAuthorize("hasRole('FEED_MILL_MANAGER')")
    public void saveFormulation(@AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable Long feedTypeId) {
        service.saveFormulation(feedTypeId, principal.username());
    }

    @GetMapping("/feed-types/{feedTypeId}/formulation/history")
    public PageResponse<FormulationHistoryGroupResponse> formulationHistory(@PathVariable Long feedTypeId,
                                                                              @PageableDefault(size = 10) Pageable pageable) {
        return PageResponse.from(service.formulationHistory(feedTypeId, pageable)
                .map(g -> FormulationHistoryGroupResponse.from(g, service.formulationHistoryItems(g))));
    }

    // ── Feed production ───────────────────────────────────────────────────

    @GetMapping("/production/check")
    public ProductionCheckResponse checkProduction(@RequestParam Long feedTypeId, @RequestParam double qtyTons) {
        return service.checkProduction(feedTypeId, qtyTons);
    }

    @PostMapping("/production")
    @PreAuthorize("hasRole('FEED_MILL_MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductionLogResponse runProduction(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                                @Valid @RequestBody CreateProductionRequest request) {
        FeedProductionLogEntry entry = service.runProduction(request, principal.username());
        return ProductionLogResponse.from(entry, service.isEditable(entry.getOccurredAt()));
    }

    @PatchMapping("/production/{id}")
    @PreAuthorize("hasRole('FEED_MILL_MANAGER')")
    public ProductionLogResponse updateProductionEntry(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                                         @PathVariable Long id,
                                                         @Valid @RequestBody UpdateProductionRequest request) {
        FeedProductionLogEntry entry = service.updateProductionEntry(id, request, principal.username());
        return ProductionLogResponse.from(entry, service.isEditable(entry.getOccurredAt()));
    }

    @GetMapping("/production")
    public PageResponse<ProductionLogResponse> productionHistory(@PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(service.productionHistory(pageable)
                .map(e -> ProductionLogResponse.from(e, service.isEditable(e.getOccurredAt()))));
    }

    @GetMapping("/production/summary")
    public List<ProductionSummaryRow> productionSummary() {
        return service.productionSummary();
    }

    // ── Fish feed stock ───────────────────────────────────────────────────

    @GetMapping("/fish-feed")
    public List<FishFeedStockResponse> listFishFeedStock() {
        return service.listFishFeedStock().stream()
                .map(s -> FishFeedStockResponse.from(s, service.isFishFeedOpeningLocked(s.getType())))
                .toList();
    }

    @PatchMapping("/fish-feed/{type}/opening")
    @PreAuthorize("hasRole('FEED_MILL_MANAGER')")
    public void setFishFeedOpening(@PathVariable String type, @Valid @RequestBody UpdateQuantityRequest request) {
        service.setFishFeedOpening(type, request);
    }

    @PostMapping("/fish-feed/{type}/opening/lock")
    @PreAuthorize("hasRole('FEED_MILL_MANAGER')")
    public void lockFishFeedOpening(@PathVariable String type) {
        service.lockFishFeedOpening(type);
    }

    // ── Fish feed collection ──────────────────────────────────────────────

    @PostMapping("/collections")
    @PreAuthorize("hasRole('FEED_MILL_MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public CollectionLogResponse submitCollection(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                                   @Valid @RequestBody CreateCollectionRequest request) {
        FeedCollectionLogEntry entry = service.submitCollection(request, principal.username());
        return CollectionLogResponse.from(entry, service.isEditable(entry.getOccurredAt()));
    }

    @PatchMapping("/collections/{id}")
    @PreAuthorize("hasRole('FEED_MILL_MANAGER')")
    public CollectionLogResponse updateCollectionEntry(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                                         @PathVariable Long id,
                                                         @Valid @RequestBody UpdateCollectionRequest request) {
        FeedCollectionLogEntry entry = service.updateCollectionEntry(id, request, principal.username());
        return CollectionLogResponse.from(entry, service.isEditable(entry.getOccurredAt()));
    }

    @GetMapping("/collections")
    public PageResponse<CollectionLogResponse> collectionHistory(@PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(service.collectionHistory(pageable)
                .map(e -> CollectionLogResponse.from(e, service.isEditable(e.getOccurredAt()))));
    }
}
