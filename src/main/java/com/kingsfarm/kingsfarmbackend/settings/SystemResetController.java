package com.kingsfarm.kingsfarmbackend.settings;

import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Administrator-only "go live" reset. Requires the caller to type the exact confirmation word. */
@RestController
@RequestMapping("/api/v1/admin/system-reset")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class SystemResetController {

    static final String CONFIRMATION = "RESET";

    private final SystemResetService service;

    public SystemResetController(SystemResetService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<?> reset(@RequestBody ResetRequest request) {
        if (!CONFIRMATION.equals(request.confirmation())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Type RESET to confirm."));
        }
        service.resetOperationalData();
        return ResponseEntity.ok(Map.of("message", "All records cleared. The system is ready for real use."));
    }

    public record ResetRequest(@NotBlank String confirmation) {
    }
}
