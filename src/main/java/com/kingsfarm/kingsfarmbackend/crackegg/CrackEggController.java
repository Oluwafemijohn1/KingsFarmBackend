package com.kingsfarm.kingsfarmbackend.crackegg;

import com.kingsfarm.kingsfarmbackend.common.PageResponse;
import com.kingsfarm.kingsfarmbackend.crackegg.dto.*;
import com.kingsfarm.kingsfarmbackend.security.AuthenticatedPrincipal;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Same role split as every other module: Administrator + Crack Egg Manager read, only Crack Egg Manager writes. */
@RestController
@RequestMapping("/api/v1/crack-egg")
@PreAuthorize("hasAnyRole('ADMINISTRATOR', 'CRACK_EGG_MANAGER')")
public class CrackEggController {

    private final CrackEggService service;

    public CrackEggController(CrackEggService service) {
        this.service = service;
    }

    @GetMapping("/stock")
    public StockSummaryResponse stockSummary() {
        return service.stockSummary();
    }

    @PatchMapping("/stock/good-opening")
    @PreAuthorize("hasRole('CRACK_EGG_MANAGER')")
    public void setGcOpening(@Valid @RequestBody UpdateIntValueRequest request) {
        service.setGcOpening(request);
    }

    @PostMapping("/stock/good-opening/lock")
    @PreAuthorize("hasRole('CRACK_EGG_MANAGER')")
    public void lockGcOpening() {
        service.lockGcOpening();
    }

    @PatchMapping("/stock/rough-opening")
    @PreAuthorize("hasRole('CRACK_EGG_MANAGER')")
    public void setRcOpening(@Valid @RequestBody UpdateIntValueRequest request) {
        service.setRcOpening(request);
    }

    @PostMapping("/stock/rough-opening/lock")
    @PreAuthorize("hasRole('CRACK_EGG_MANAGER')")
    public void lockRcOpening() {
        service.lockRcOpening();
    }

    @PatchMapping("/stock/selling-price")
    @PreAuthorize("hasRole('CRACK_EGG_MANAGER')")
    public void setSellingPrice(@Valid @RequestBody UpdateLongValueRequest request) {
        service.setGcSellingPrice(request);
    }

    @PatchMapping("/stock/feed-mill")
    @PreAuthorize("hasRole('CRACK_EGG_MANAGER')")
    public void setFeedMillUsage(@Valid @RequestBody UpdateIntValueRequest request) {
        service.setRcFeedMill(request);
    }

    // ── Good Crack Sales ──────────────────────────────────────────────────

    @PostMapping("/sales")
    @PreAuthorize("hasRole('CRACK_EGG_MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public GcSaleResponse createSale(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                      @Valid @RequestBody CreateGcSaleRequest request) {
        GcSaleTransaction txn = service.createSale(request, principal.username());
        return GcSaleResponse.from(txn, service.isEditable(txn.getOccurredAt()));
    }

    @PatchMapping("/sales/{id}")
    @PreAuthorize("hasRole('CRACK_EGG_MANAGER')")
    public GcSaleResponse updateSale(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                      @PathVariable Long id,
                                      @Valid @RequestBody UpdateGcSaleRequest request) {
        GcSaleTransaction txn = service.updateSale(id, request, principal.username());
        return GcSaleResponse.from(txn, service.isEditable(txn.getOccurredAt()));
    }

    @GetMapping("/sales")
    public PageResponse<GcSaleResponse> saleHistory(@PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(service.saleHistory(pageable)
                .map(t -> GcSaleResponse.from(t, service.isEditable(t.getOccurredAt()))));
    }

    // ── Gift log ──────────────────────────────────────────────────────────

    @PatchMapping("/gift/qty")
    @PreAuthorize("hasRole('CRACK_EGG_MANAGER')")
    public void setGiftQty(@Valid @RequestBody UpdateIntValueRequest request) {
        service.setGcGiftQty(request);
    }

    @PatchMapping("/gift/recipient")
    @PreAuthorize("hasRole('CRACK_EGG_MANAGER')")
    public void setGiftRecipient(@RequestBody UpdateTextValueRequest request) {
        service.setGcGiftRecipient(request);
    }

    @PatchMapping("/gift/authorizer")
    @PreAuthorize("hasRole('CRACK_EGG_MANAGER')")
    public void setGiftAuthorizer(@RequestBody UpdateTextValueRequest request) {
        service.setGcGiftAuthorizer(request);
    }

    @PostMapping("/gift/log")
    @PreAuthorize("hasRole('CRACK_EGG_MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public GiftLogEntryResponse saveGiftSnapshot(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        CrackEggGiftLogEntry entry = service.saveGiftSnapshot(principal.username());
        return GiftLogEntryResponse.from(entry, service.isEditable(entry.getOccurredAt()));
    }

    @PatchMapping("/gift/log/{id}")
    @PreAuthorize("hasRole('CRACK_EGG_MANAGER')")
    public GiftLogEntryResponse updateGiftEntry(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                                 @PathVariable Long id,
                                                 @Valid @RequestBody UpdateGiftLogEntryRequest request) {
        CrackEggGiftLogEntry entry = service.updateGiftEntry(id, request, principal.username());
        return GiftLogEntryResponse.from(entry, service.isEditable(entry.getOccurredAt()));
    }

    @GetMapping("/gift/log")
    public PageResponse<GiftLogEntryResponse> giftLogHistory(@PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(service.giftLogHistory(pageable)
                .map(e -> GiftLogEntryResponse.from(e, service.isEditable(e.getOccurredAt()))));
    }
}
