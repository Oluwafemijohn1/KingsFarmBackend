package com.kingsfarm.kingsfarmbackend.production;

import com.kingsfarm.kingsfarmbackend.common.CatKey;
import com.kingsfarm.kingsfarmbackend.common.PageResponse;
import com.kingsfarm.kingsfarmbackend.common.reports.ReportTableResponse;
import com.kingsfarm.kingsfarmbackend.production.dto.*;
import com.kingsfarm.kingsfarmbackend.security.AuthenticatedPrincipal;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Same role split as Bird Stock: Administrator + Production Manager can read, only Production Manager can write. */
@RestController
@RequestMapping("/api/v1/production")
@PreAuthorize("hasAnyRole('ADMINISTRATOR', 'MANAGING_DIRECTOR', 'PRODUCTION_MANAGER')")
public class ProductionController {

    private final ProductionService service;

    public ProductionController(ProductionService service) {
        this.service = service;
    }

    @GetMapping("/pen-entries/today")
    public List<ProductionPenEntryResponse> getTodayPenEntries() {
        return service.getTodayPenEntries();
    }

    /** Pen entries for a chosen day (defaults to today). Past days are read-only unless inside the grace window or approved. */
    @GetMapping("/pen-entries")
    public List<ProductionPenEntryResponse> getPenEntries(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.getPenEntriesFor(date);
    }

    @PatchMapping("/pen-entries/{penId}")
    @PreAuthorize("hasRole('PRODUCTION_MANAGER')")
    public ProductionPenEntryResponse updatePenEntry(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                                       @PathVariable Long penId,
                                                       @Valid @RequestBody UpdatePenEntryRequest request) {
        return service.updatePenEntry(penId, request, principal.username());
    }

    @GetMapping("/pen-entries/history")
    public PageResponse<ProductionPenEntryResponse> penEntryHistory(@RequestParam(required = false) Long penId,
                                                                      @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(service.penEntryHistory(penId, pageable));
    }

    // Broadened past the class-level role list — the Crack Egg Manager's own
    // page (CrackEggView's Classify section) reads this to show Total Crack
    // Use and the current Good/Rough split before editing it via
    // updateClassifyFields below.
    @GetMapping("/day-state/today")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'MANAGING_DIRECTOR', 'PRODUCTION_MANAGER', 'CRACK_EGG_MANAGER')")
    public ProductionDayStateResponse getTodayDayState(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        if (principal.role() == com.kingsfarm.kingsfarmbackend.user.Role.CRACK_EGG_MANAGER) {
            return service.toResponseForCrackManager(service.getTodayDayState());
        }
        return service.toResponse(service.getTodayDayState());
    }

    /** Day state for a chosen day (defaults to today) — the Crack Egg page's day picker. */
    @GetMapping("/day-state")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'MANAGING_DIRECTOR', 'PRODUCTION_MANAGER', 'CRACK_EGG_MANAGER')")
    public ProductionDayStateResponse getDayState(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (principal.role() == com.kingsfarm.kingsfarmbackend.user.Role.CRACK_EGG_MANAGER) {
            return service.toResponseForCrackManager(service.updateClassifyResponseState(date));
        }
        return service.toResponse(service.dayStateForView(date));
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

    // Classifying Total Crack Use into Good/Rough is the Crack Egg Manager's
    // call, not Production's — CrackEggView.tsx's Classify section calls
    // this, not ProductionView.tsx (which now shows the result read-only).
    // Still writes to ProductionDayState (see ProductionService.updateClassifyFields's
    // javadoc for why the data stays there).
    @PatchMapping("/day-state/classify-fields")
    @PreAuthorize("hasRole('CRACK_EGG_MANAGER')")
    public ProductionDayStateResponse updateClassifyFields(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                                             @Valid @RequestBody UpdateClassifyFieldsRequest request) {
        return service.toResponseForCrackManager(service.updateClassifyFields(request, principal.username()));
    }

    @GetMapping("/day-state/history")
    public PageResponse<ProductionDayStateResponse> dayStateHistory(@PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(service.dayStateHistory(pageable).map(service::toResponse));
    }

    // ── Reports (BACKEND_PLAN.md §8) ─────────────────────────────────────

    @GetMapping("/reports/daily")
    public ReportTableResponse dailyReport(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return service.dailyReport(start, end);
    }

    @GetMapping("/reports/monthly")
    public ReportTableResponse monthlyReport(@RequestParam(defaultValue = "12") int months) {
        return service.monthlyReport(months);
    }
}
