package com.kingsfarm.kingsfarmbackend.backfill;

import com.kingsfarm.kingsfarmbackend.backfill.dto.CloseDayRequest;
import com.kingsfarm.kingsfarmbackend.backfill.dto.DayStatusResponse;
import com.kingsfarm.kingsfarmbackend.common.Mod;
import com.kingsfarm.kingsfarmbackend.security.AuthenticatedPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/day-closure")
public class DayClosureController {

    private final DayClosureService service;

    public DayClosureController(DayClosureService service) {
        this.service = service;
    }

    /** Which day this module is currently working on, and how far behind it is. */
    @GetMapping("/status")
    public DayStatusResponse status(@RequestParam Mod module) {
        return service.status(module);
    }

    @PostMapping("/close")
    @PreAuthorize("!hasRole('ADMINISTRATOR')")
    public DayStatusResponse close(@AuthenticationPrincipal AuthenticatedPrincipal principal, @Valid @RequestBody CloseDayRequest request) {
        return service.close(request.module(), request.date(), Boolean.TRUE.equals(request.confirmNothingElse()), principal);
    }

    /** Entries still missing before this (past) day can be closed. */
    @GetMapping("/missing")
    public java.util.List<String> missing(@RequestParam Mod module, @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate date) {
        return service.missingFor(module, date);
    }

    /** Whether managers of every module may go back to an earlier day (Feed Mill always can). */
    @GetMapping("/go-back-enabled")
    public java.util.Map<String, Boolean> goBackEnabled() {
        return java.util.Map.of("enabled", service.goBackEnabled());
    }

    /** Go back to an earlier day (reopens it and every later day). Feed Mill always; other modules while the switch is on. */
    @PostMapping("/reopen")
    public DayStatusResponse reopen(@AuthenticationPrincipal AuthenticatedPrincipal principal, @RequestParam Mod module,
                                    @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from) {
        return service.reopenFrom(module, from, principal);
    }
}
