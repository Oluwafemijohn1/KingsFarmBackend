package com.kingsfarm.kingsfarmbackend.backfill;

import com.kingsfarm.kingsfarmbackend.backfill.dto.*;
import com.kingsfarm.kingsfarmbackend.common.Mod;
import com.kingsfarm.kingsfarmbackend.common.PageResponse;
import com.kingsfarm.kingsfarmbackend.openingstock.RequestStatus;
import com.kingsfarm.kingsfarmbackend.security.AuthenticatedPrincipal;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/backfill-requests")
public class BackfillController {

    private final BackfillRequestService service;
    private final EditWindowService editWindow;

    public BackfillController(BackfillRequestService service, EditWindowService editWindow) {
        this.service = service;
        this.editWindow = editWindow;
    }

    @GetMapping("/enabled")
    public boolean enabled() {
        return editWindow.isEnabled();
    }

    /** Can I edit this module's records for that day right now, or have I already asked? */
    @GetMapping("/window")
    public EditWindowResponse window(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                     @RequestParam Mod module, @RequestParam LocalDate date) {
        return service.window(module, date, principal.userId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("!hasRole('ADMINISTRATOR')")
    public BackfillRequestResponse create(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                          @Valid @RequestBody CreateBackfillRequestRequest request) {
        return service.create(request, principal.userId());
    }

    @GetMapping("/mine")
    public PageResponse<BackfillRequestResponse> mine(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                                      @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(service.listMine(principal.userId(), pageable));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public PageResponse<BackfillRequestResponse> list(@RequestParam(required = false) RequestStatus status,
                                                      @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(service.list(status, pageable));
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public BackfillRequestResponse resolve(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                           @PathVariable Long id, @Valid @RequestBody ResolveBackfillRequestRequest request) {
        return service.resolve(id, request.decision(), principal.userId());
    }
}
