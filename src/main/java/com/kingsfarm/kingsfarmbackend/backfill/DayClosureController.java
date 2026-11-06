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
        return service.close(request.module(), request.date(), principal);
    }
}
