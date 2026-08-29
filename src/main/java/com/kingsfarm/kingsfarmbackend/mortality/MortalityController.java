package com.kingsfarm.kingsfarmbackend.mortality;

import com.kingsfarm.kingsfarmbackend.common.PageResponse;
import com.kingsfarm.kingsfarmbackend.mortality.dto.*;
import com.kingsfarm.kingsfarmbackend.security.AuthenticatedPrincipal;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Same role split as every other module: Administrator + Mortality Manager read, only Mortality Manager writes. */
@RestController
@RequestMapping("/api/v1/mortality")
@PreAuthorize("hasAnyRole('ADMINISTRATOR', 'MORTALITY_MANAGER')")
public class MortalityController {

    private final MortalityService service;

    public MortalityController(MortalityService service) {
        this.service = service;
    }

    // ── Stock overview ───────────────────────────────────────────────────

    @GetMapping("/stock")
    public List<MortCategoryStockRow> stockOverview() {
        return service.stockOverview();
    }

    @PatchMapping("/stock/{category}/opening")
    @PreAuthorize("hasRole('MORTALITY_MANAGER')")
    public void setOpening(@PathVariable MortCat category, @Valid @RequestBody UpdateOpeningRequest request) {
        service.setOpening(category, request);
    }

    @PostMapping("/stock/{category}/opening/lock")
    @PreAuthorize("hasRole('MORTALITY_MANAGER')")
    public void lockOpening(@PathVariable MortCat category) {
        service.lockOpening(category);
    }

    // ── Pen mortality entry ──────────────────────────────────────────────

    @GetMapping("/pens/today")
    public List<MortPenEntryResponse> getTodayPenEntries() {
        return service.getTodayPenEntries().stream()
                .map(e -> MortPenEntryResponse.from(e, service.isEditable(e.getEntryDate())))
                .toList();
    }

    @PatchMapping("/pens/{penId}")
    @PreAuthorize("hasRole('MORTALITY_MANAGER')")
    public MortPenEntryResponse updatePenEntry(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                                @PathVariable Long penId,
                                                @Valid @RequestBody UpdateMortPenEntryRequest request) {
        MortPenEntry entry = service.updatePenEntry(penId, request, principal.username());
        return MortPenEntryResponse.from(entry, service.isEditable(entry.getEntryDate()));
    }

    // ── Sales ─────────────────────────────────────────────────────────────

    @PostMapping("/sales")
    @PreAuthorize("hasRole('MORTALITY_MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public MortSaleResponse createSale(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                        @Valid @RequestBody CreateMortSaleRequest request) {
        MortSaleEntry entry = service.createSale(request, principal.username());
        return MortSaleResponse.from(entry, service.isEditableInstant(entry.getOccurredAt()));
    }

    @PatchMapping("/sales/{id}")
    @PreAuthorize("hasRole('MORTALITY_MANAGER')")
    public MortSaleResponse updateSale(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                        @PathVariable Long id,
                                        @Valid @RequestBody UpdateMortSaleRequest request) {
        MortSaleEntry entry = service.updateSale(id, request, principal.username());
        return MortSaleResponse.from(entry, service.isEditableInstant(entry.getOccurredAt()));
    }

    @GetMapping("/sales")
    public PageResponse<MortSaleResponse> saleHistory(@PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(service.saleHistory(pageable)
                .map(e -> MortSaleResponse.from(e, service.isEditableInstant(e.getOccurredAt()))));
    }

    // ── Gift ──────────────────────────────────────────────────────────────

    @PostMapping("/gift")
    @PreAuthorize("hasRole('MORTALITY_MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public GiftLogEntryResponse saveGift(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                          @Valid @RequestBody SaveGiftRequest request) {
        MortGiftLogEntry entry = service.saveGift(request, principal.username());
        return GiftLogEntryResponse.from(entry, service.isEditableInstant(entry.getOccurredAt()));
    }

    @PatchMapping("/gift/{id}")
    @PreAuthorize("hasRole('MORTALITY_MANAGER')")
    public GiftLogEntryResponse updateGiftEntry(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                                 @PathVariable Long id,
                                                 @Valid @RequestBody UpdateGiftLogEntryRequest request) {
        MortGiftLogEntry entry = service.updateGiftEntry(id, request, principal.username());
        return GiftLogEntryResponse.from(entry, service.isEditableInstant(entry.getOccurredAt()));
    }

    @GetMapping("/gift")
    public PageResponse<GiftLogEntryResponse> giftLogHistory(@PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(service.giftLogHistory(pageable)
                .map(e -> GiftLogEntryResponse.from(e, service.isEditableInstant(e.getOccurredAt()))));
    }

    // ── Catfish & Disposal ────────────────────────────────────────────────

    @GetMapping("/catfish-disposal/today")
    public CatfishDisposalResponse todayCatfishDisposal() {
        MortCatfishDisposalState state = service.todayCatfishDisposal();
        return CatfishDisposalResponse.from(state, service.greenAvailable(), service.pmRejectAvailable(), service.isEditable(state.getEntryDate()));
    }

    @PatchMapping("/catfish-disposal")
    @PreAuthorize("hasRole('MORTALITY_MANAGER')")
    public CatfishDisposalResponse updateCatfishDisposal(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                                           @Valid @RequestBody UpdateCatfishDisposalRequest request) {
        MortCatfishDisposalState state = service.updateCatfishDisposal(request, principal.username());
        return CatfishDisposalResponse.from(state, service.greenAvailable(), service.pmRejectAvailable(), service.isEditable(state.getEntryDate()));
    }
}
