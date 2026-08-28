package com.kingsfarm.kingsfarmbackend.settings;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/security-settings")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class SecuritySettingsController {

    private final SecuritySettingsService service;

    public SecuritySettingsController(SecuritySettingsService service) {
        this.service = service;
    }

    @GetMapping
    public SecuritySettings get() {
        return service.get();
    }

    @PutMapping
    public SecuritySettings update(@RequestBody UpdateRequest request) {
        return service.update(request.sessionTimeoutMinutes(), request.lockoutAttempts(), request.passwordMinLength());
    }

    public record UpdateRequest(
            @Min(5) @Max(480) int sessionTimeoutMinutes,
            @Min(3) @Max(10) int lockoutAttempts,
            @Min(6) @Max(64) int passwordMinLength
    ) {
    }
}
