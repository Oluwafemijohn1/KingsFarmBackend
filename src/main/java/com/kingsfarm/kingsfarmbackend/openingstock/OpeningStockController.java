package com.kingsfarm.kingsfarmbackend.openingstock;

import com.kingsfarm.kingsfarmbackend.common.Mod;
import com.kingsfarm.kingsfarmbackend.common.PageResponse;
import com.kingsfarm.kingsfarmbackend.openingstock.dto.*;
import com.kingsfarm.kingsfarmbackend.security.AuthenticatedPrincipal;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Shared across every module — a manager checks/asks here regardless of
 * which module they're in, so this doesn't live under any one module's
 * package. Fine-grained "can this caller touch this module's opening
 * stock" is enforced by the calling module's own controller in Phase 3;
 * this endpoint set only needs to distinguish "any authenticated user" from
 * "Administrator."
 */
@RestController
@RequestMapping("/api/v1/opening-stock")
public class OpeningStockController {

    private final OpeningStockLockService lockService;
    private final OpeningStockRequestService requestService;

    public OpeningStockController(OpeningStockLockService lockService, OpeningStockRequestService requestService) {
        this.lockService = lockService;
        this.requestService = requestService;
    }

    @GetMapping("/lock")
    public LockStatusResponse getLockStatus(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                             @RequestParam Mod module, @RequestParam String scope) {
        boolean pendingByMe = requestService.hasPendingRequestByUser(module, scope, principal.userId());
        return new LockStatusResponse(lockService.isLocked(module, scope), pendingByMe);
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public OpeningStockRequestResponse createRequest(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                                       @Valid @RequestBody CreateOpeningStockRequestRequest request) {
        return OpeningStockRequestResponse.from(requestService.create(request, principal.userId()));
    }

    @GetMapping("/requests")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public PageResponse<OpeningStockRequestResponse> listRequests(@RequestParam(required = false) RequestStatus status,
                                                                    @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(requestService.list(status, pageable).map(OpeningStockRequestResponse::from));
    }

    @PostMapping("/requests/{id}/resolve")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public OpeningStockRequestResponse resolve(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                                @PathVariable Long id,
                                                @Valid @RequestBody ResolveOpeningStockRequestRequest request) {
        return OpeningStockRequestResponse.from(requestService.resolve(id, request.decision(), principal.userId()));
    }
}
