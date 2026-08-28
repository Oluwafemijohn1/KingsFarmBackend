package com.kingsfarm.kingsfarmbackend.birdstock;

import com.kingsfarm.kingsfarmbackend.birdstock.dto.*;
import com.kingsfarm.kingsfarmbackend.common.PageResponse;
import com.kingsfarm.kingsfarmbackend.security.AuthenticatedPrincipal;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Reads are open to Administrator + Production Manager (matches the ACCESS
 * map — Administrator reaches every module read-only via the admin UI).
 * Writes are Production Manager only: the frontend shows Administrators a
 * "View Only · Managers enter this record" banner here, on purpose — an
 * Administrator's only lever over this data is approving/denying Opening
 * Stock requests, never editing pen records directly.
 */
@RestController
@RequestMapping("/api/v1/bird-stock")
@PreAuthorize("hasAnyRole('ADMINISTRATOR', 'PRODUCTION_MANAGER')")
public class BirdStockController {

    private final BirdStockService service;

    public BirdStockController(BirdStockService service) {
        this.service = service;
    }

    @GetMapping("/pens")
    public List<PenResponse> listPens(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return service.listPens(includeInactive).stream().map(PenResponse::from).toList();
    }

    @PostMapping("/pens")
    @PreAuthorize("hasRole('PRODUCTION_MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public PenResponse addPen(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                               @Valid @RequestBody CreatePenRequest request) {
        return PenResponse.from(service.addPen(request, principal.username()));
    }

    @DeleteMapping("/pens/{id}")
    @PreAuthorize("hasRole('PRODUCTION_MANAGER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removePen(@PathVariable Long id) {
        service.deactivatePen(id);
    }

    @GetMapping("/records/today")
    public List<BirdPenRecordResponse> getTodayRecords() {
        return service.getTodayRecords().stream()
                .map(r -> BirdPenRecordResponse.from(r, service.isEditable(r), service.isOpeningLocked(r.getPen())))
                .toList();
    }

    @PatchMapping("/records/{penId}")
    @PreAuthorize("hasRole('PRODUCTION_MANAGER')")
    public BirdPenRecordResponse updateRecord(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                               @PathVariable Long penId,
                                               @Valid @RequestBody UpdateBirdPenRecordRequest request) {
        BirdPenRecord record = service.updateRecord(penId, request, principal.username());
        return BirdPenRecordResponse.from(record, service.isEditable(record), service.isOpeningLocked(record.getPen()));
    }

    @PostMapping("/records/{penId}/lock-opening")
    @PreAuthorize("hasRole('PRODUCTION_MANAGER')")
    public void lockOpening(@PathVariable Long penId) {
        service.lockOpening(penId);
    }

    @GetMapping("/records/history")
    public PageResponse<BirdPenRecordResponse> history(@RequestParam(required = false) Long penId,
                                                         @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(service.history(penId, pageable)
                .map(r -> BirdPenRecordResponse.from(r, service.isEditable(r), service.isOpeningLocked(r.getPen()))));
    }
}
