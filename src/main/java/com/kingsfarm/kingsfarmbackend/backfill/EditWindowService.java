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
 * The single answer to "may the current user enter or edit this module's records for this day?"
 *
 * With sequential entry on (the normal setting):
 *  - a day before the go-live start date, or after today, is never open;
 *  - a day that is NOT closed is open only if it is the module's first unclosed day, so days are
 *    always entered in order, oldest first, and a later day can never be entered before an earlier one;
 *  - a day that IS closed stays editable for the 24-hour grace (yesterday and today); anything
 *    older needs an approved, unexpired {@link BackfillRequest} from the Administrator.
 * With it off, only today is open (the original behaviour).
 */
@Service
public class EditWindowService {

    private final BackfillRequestRepository repository;
    private final DayClosureService closures;
    private final boolean enabled;

    public EditWindowService(BackfillRequestRepository repository, DayClosureService closures,
                             @org.springframework.beans.factory.annotation.Value("${app.backfill.enabled:false}") boolean enabled) {
        this.repository = repository;
        this.closures = closures;
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Today or yesterday. */
    public boolean isGraceOpen(LocalDate day) {
        LocalDate today = LocalDate.now();
        if (!enabled) {
            return day.isEqual(today);
        }
        return !day.isAfter(today) && !day.isBefore(today.minusDays(1));
    }

    public boolean isOpen(Mod module, LocalDate day) {
        LocalDate today = LocalDate.now();
        if (day.isAfter(today)) {
            return false;
        }
        if (!enabled) {
            return day.isEqual(today);
        }
        if (day.isBefore(closures.startDate())) {
            return false;
        }
        if (closures.isClosed(module, day)) {
            return isGraceOpen(day) || hasApproval(module, day);
        }
        return day.equals(closures.firstUnclosed(module));
    }

    private boolean hasApproval(Mod module, LocalDate day) {
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
