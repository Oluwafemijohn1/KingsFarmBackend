package com.kingsfarm.kingsfarmbackend.relief;

import com.kingsfarm.kingsfarmbackend.common.PageResponse;
import com.kingsfarm.kingsfarmbackend.relief.dto.CreateReliefGrantRequest;
import com.kingsfarm.kingsfarmbackend.relief.dto.ReliefGrantResponse;
import com.kingsfarm.kingsfarmbackend.security.AuthenticatedPrincipal;
import com.kingsfarm.kingsfarmbackend.user.dto.UserSummaryResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Backs AdminView's Relief Access tab — Administrator-only, matching every action in ReliefAccessView.tsx. */
@RestController
@RequestMapping("/api/v1/admin/relief-access")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class ReliefAccessController {

    private final ReliefAccessService service;

    public ReliefAccessController(ReliefAccessService service) {
        this.service = service;
    }

    @GetMapping("/eligible-users")
    public List<UserSummaryResponse> eligibleUsers() {
        return service.eligibleUsers().stream().map(UserSummaryResponse::from).toList();
    }

    @GetMapping("/active")
    public List<ReliefGrantResponse> activeGrants() {
        return service.activeGrants().stream().map(ReliefGrantResponse::from).toList();
    }

    @GetMapping("/past")
    public PageResponse<ReliefGrantResponse> pastGrants(@PageableDefault(size = 10) Pageable pageable) {
        return PageResponse.from(service.pastGrants(pageable).map(ReliefGrantResponse::from));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReliefGrantResponse grant(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                      @Valid @RequestBody CreateReliefGrantRequest request) {
        return ReliefGrantResponse.from(service.grant(request, principal.username()));
    }

    @PostMapping("/{id}/revoke")
    public ReliefGrantResponse revoke(@AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable Long id) {
        return ReliefGrantResponse.from(service.revoke(id, principal.username()));
    }
}
