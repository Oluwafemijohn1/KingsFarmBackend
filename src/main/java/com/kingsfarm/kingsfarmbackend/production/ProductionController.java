package com.kingsfarm.kingsfarmbackend.production;

import com.kingsfarm.kingsfarmbackend.common.CatKey;
import com.kingsfarm.kingsfarmbackend.common.PageResponse;
import com.kingsfarm.kingsfarmbackend.production.dto.*;
import com.kingsfarm.kingsfarmbackend.security.AuthenticatedPrincipal;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Same role split as Bird Stock: Administrator + Production Manager can read, only Production Manager can write. */
@RestController
@RequestMapping("/api/v1/production")
@PreAuthorize("hasAnyRole('ADMINISTRATOR', 'PRODUCTION_MANAGER')")
public class ProductionController {

    private final ProductionService service;

    public ProductionController(ProductionService service) {
        this.service = service;
    }

    @GetMapping("/pen-entries/today")
    public List<ProductionPenEntryResponse> getTodayPenEntries() {
        return service.getTodayPenEntries().stream()
                .map(entry -> {
                    int birdClosing = service.birdClosingFor(entry.getPen(), entry.getEntryDate());
                    String pct = service.productionPercent(entry.total(), birdClosing);
                    return ProductionPenEntryResponse.from(entry, birdClosing, pct, service.isEditable(entry.getEntryDate()));
                })
                .toList();
    }

    @PatchMapping("/pen-entries/{penId}")
    @PreAuthorize("hasRole('PRODUCTION_MANAGER')")
    public ProductionPenEntryResponse updatePenEntry(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                                       @PathVariable Long penId,
                                                       @Valid @RequestBody UpdatePenEntryRequest request) {
        ProductionPenEntry entry = service.updatePenEntry(penId, request, principal.username());
        int birdClosing = service.birdClosingFor(entry.getPen(), entry.getEntryDate());
        String pct = service.productionPercent(entry.total(), birdClosing);
        return ProductionPenEntryResponse.from(entry, birdClosing, pct, service.isEditable(entry.getEntryDate()));
    }

    @GetMapping("/pen-entries/history")
    public PageResponse<ProductionPenEntryResponse> penEntryHistory(@RequestParam(required = false) Long penId,
                                                                      @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(service.penEntryHistory(penId, pageable)
                .map(entry -> {
                    int birdClosing = service.birdClosingFor(entry.getPen(), entry.getEntryDate());
                    String pct = service.productionPercent(entry.total(), birdClosing);
                    return ProductionPenEntryResponse.from(entry, birdClosing, pct, service.isEditable(entry.getEntryDate()));
                }));
    }

    @GetMapping("/day-state/today")
    public ProductionDayStateResponse getTodayDayState() {
        return service.toResponse(service.getTodayDayState());
    }

    @PatchMapping("/day-state/cat-opening")
    @PreAuthorize("hasRole('PRODUCTION_MANAGER')")
    public ProductionDayStateResponse updateCatOpening(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                                         @Valid @RequestBody UpdateCatOpeningRequest request) {
        return service.toResponse(service.updateCatOpening(request, principal.username()));
    }

    @PostMapping("/day-state/cat-opening/{category}/lock")
    @PreAuthorize("hasRole('PRODUCTION_MANAGER')")
    public void lockCatOpening(@PathVariable CatKey category) {
        service.lockCatOpening(category);
    }

    @PatchMapping("/day-state/crack-fields")
    @PreAuthorize("hasRole('PRODUCTION_MANAGER')")
    public ProductionDayStateResponse updateCrackFields(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                                          @Valid @RequestBody UpdateCrackFieldsRequest request) {
        return service.toResponse(service.updateCrackFields(request, principal.username()));
    }

    @GetMapping("/day-state/history")
    public PageResponse<ProductionDayStateResponse> dayStateHistory(@PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(service.dayStateHistory(pageable).map(service::toResponse));
    }
}
