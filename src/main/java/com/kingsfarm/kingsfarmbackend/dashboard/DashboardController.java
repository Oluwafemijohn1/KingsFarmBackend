package com.kingsfarm.kingsfarmbackend.dashboard;

import com.kingsfarm.kingsfarmbackend.security.AuthenticatedPrincipal;
import com.kingsfarm.kingsfarmbackend.systemlog.SystemLogResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * No class-level @PreAuthorize beyond SecurityConfig's default
 * "anyRequest().authenticated()" — every role lands on DashboardView, so
 * every role needs to be able to call this; the module scope each caller
 * actually sees is derived from their own role server-side in
 * DashboardService, not left to the client to request.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/activity")
    public List<SystemLogResponse> recentActivity(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                                    @RequestParam(defaultValue = "6") int limit) {
        return service.recentActivity(principal.role(), limit);
    }
}
