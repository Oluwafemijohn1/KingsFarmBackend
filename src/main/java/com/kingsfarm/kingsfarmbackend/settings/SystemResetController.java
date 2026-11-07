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
        java.util.Set<String> groups = new java.util.HashSet<>();
        if (request.groups() != null && !request.groups().isEmpty()) {
            groups.addAll(request.groups());
        } else {
            // Older clients: everything operational, plus the optional extras they could tick.
            groups.addAll(SystemResetService.ALL_RECORD_GROUPS);
            if (Boolean.TRUE.equals(request.deleteOtherUsers())) groups.add("users");
            if (Boolean.TRUE.equals(request.deletePens())) groups.add("pens");
            if (Boolean.TRUE.equals(request.deleteFeedSetup())) groups.add("feedSetup");
        }
        service.resetOperationalData(groups);
        return ResponseEntity.ok(Map.of("message", "All records cleared. The system is ready for real use."));
    }

    public record ResetRequest(@NotBlank String confirmation, Boolean deleteOtherUsers, Boolean deletePens, Boolean deleteFeedSetup, java.util.List<String> groups) {
    }
}
