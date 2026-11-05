package com.kingsfarm.kingsfarmbackend.backfill;

import com.kingsfarm.kingsfarmbackend.common.Mod;
import com.kingsfarm.kingsfarmbackend.openingstock.RequestStatus;
import com.kingsfarm.kingsfarmbackend.security.AuthenticatedPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;

/**
 * The single answer to "may the current user enter or edit this module's
 * records for this day?": today and yesterday (24-hour grace) are always open;
 * any older day is open only while the user holds an approved, unexpired
 * {@link BackfillRequest} for that module and date.
 */
@Service
public class EditWindowService {

    private final BackfillRequestRepository repository;

    /**
     * Off by default: opening yesterday/older days for editing changes that day's
     * closing stock, and later days' openings must then be re-calculated (daily
     * snapshots — not built yet). Until then only today is editable, as before.
     */
    private final boolean enabled;

    public EditWindowService(BackfillRequestRepository repository,
                             @org.springframework.beans.factory.annotation.Value("${app.backfill.enabled:false}") boolean enabled) {
        this.repository = repository;
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isGraceOpen(LocalDate day) {
        LocalDate today = LocalDate.now();
        if (!enabled) {
            return day.isEqual(today);
        }
        return !day.isAfter(today) && !day.isBefore(today.minusDays(1));
    }

    public boolean isOpen(Mod module, LocalDate day) {
        if (isGraceOpen(day)) {
            return true;
        }
        if (!enabled) {
            return false;
        }
        if (day.isAfter(LocalDate.now())) {
            return false;
        }
        Long userId = currentUserId();
        return userId != null && repository.existsByModuleAndForDateAndRequestedBy_IdAndStatusAndExpiresAtAfter(
                module, day, userId, RequestStatus.APPROVED, Instant.now());
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedPrincipal p) {
            return p.userId();
        }
        return null;
    }
}
