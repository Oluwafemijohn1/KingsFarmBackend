package com.kingsfarm.kingsfarmbackend.backfill;

import com.kingsfarm.kingsfarmbackend.audit.Audited;
import com.kingsfarm.kingsfarmbackend.backfill.dto.DayStatusResponse;
import com.kingsfarm.kingsfarmbackend.common.Mod;
import com.kingsfarm.kingsfarmbackend.common.exception.BadRequestException;
import com.kingsfarm.kingsfarmbackend.common.exception.ForbiddenException;
import com.kingsfarm.kingsfarmbackend.security.AuthenticatedPrincipal;
import com.kingsfarm.kingsfarmbackend.user.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Per-module "day closed" bookkeeping. From the go-live start date, a module can
 * only be entered for its first unclosed day; closing that day opens the next.
 */
@Service
public class DayClosureService {

    private static final Map<Mod, Role> OWNER = new EnumMap<>(Mod.class);
    static {
        OWNER.put(Mod.BIRD_STOCK, Role.PRODUCTION_MANAGER);
        OWNER.put(Mod.PRODUCTION, Role.PRODUCTION_MANAGER);
        OWNER.put(Mod.WHOLE_EGG, Role.WHOLE_EGG_MANAGER);
        OWNER.put(Mod.CRACK_EGG, Role.CRACK_EGG_MANAGER);
        OWNER.put(Mod.FEED_MILL, Role.FEED_MILL_MANAGER);
        OWNER.put(Mod.MORTALITY, Role.MORTALITY_MANAGER);
    }

    private final DayClosureRepository repository;
    private final LocalDate startDate;

    /** Closed dates per module; small (about one entry per day) so it is simply cached and cleared on every change. */
    private final Map<Mod, Set<LocalDate>> cache = new java.util.concurrent.ConcurrentHashMap<>();

    private final DayCompletenessService completeness;

    public DayClosureService(DayClosureRepository repository,
                             DayCompletenessService completeness,
                             @Value("${app.golive.start-date:2026-11-01}") String startDate) {
        this.repository = repository;
        this.completeness = completeness;
        this.startDate = LocalDate.parse(startDate);
    }

    public LocalDate startDate() {
        return startDate;
    }

    private Set<LocalDate> closedDates(Mod module) {
        return cache.computeIfAbsent(module, m -> {
            Set<LocalDate> set = new HashSet<>();
            repository.findAllByModuleAndClosedDateGreaterThanEqual(m, startDate).forEach(c -> set.add(c.getClosedDate()));
            return set;
        });
    }

    public void invalidate() {
        cache.clear();
    }

    public boolean isClosed(Mod module, LocalDate day) {
        return closedDates(module).contains(day);
    }

    /** First day from the start date that is not closed, or null if every day up to and including today is closed. */
    public LocalDate firstUnclosed(Mod module) {
        LocalDate today = LocalDate.now();
        Set<LocalDate> closed = closedDates(module);
        for (LocalDate d = startDate; !d.isAfter(today); d = d.plusDays(1)) {
            if (!closed.contains(d)) {
                return d;
            }
        }
        return null;
    }

    @Transactional(readOnly = true)
    public DayStatusResponse status(Mod module) {
        LocalDate today = LocalDate.now();
        LocalDate working = firstUnclosed(module);
        int behind = 0;
        Set<LocalDate> closed = closedDates(module);
        for (LocalDate d = startDate; d.isBefore(today); d = d.plusDays(1)) {
            if (!closed.contains(d)) behind++;
        }
        return new DayStatusResponse(startDate, today, working, behind, closed.contains(today));
    }

    /** What is still missing before a past day can be closed (always empty for today). */
    @Transactional(readOnly = true)
    public java.util.List<String> missingFor(Mod module, LocalDate date) {
        if (!date.isBefore(LocalDate.now())) {
            return java.util.List.of();
        }
        return completeness.missing(module, date);
    }

    /** Feed Mill only: reopen every closed day from {@code from} so entries can be made from that day again. */
    @Audited(module = Mod.ADMIN, action = "Reopen Days", detail = "#module + ' from ' + #from")
    @Transactional
    public DayStatusResponse reopenFrom(Mod module, LocalDate from, AuthenticatedPrincipal principal) {
        if (module != Mod.FEED_MILL) {
            throw new BadRequestException("Going back to earlier days is only available for the Feed Mill.");
        }
        boolean allowed = principal.role() == Role.ADMINISTRATOR || principal.role() == Role.FEED_MILL_MANAGER
                || (principal.extraRoles() != null && principal.extraRoles().contains(Role.FEED_MILL_MANAGER));
        if (!allowed) {
            throw new ForbiddenException("Only the Feed Mill Manager or the Administrator can go back to an earlier day.");
        }
        if (from.isBefore(startDate) || from.isAfter(LocalDate.now())) {
            throw new BadRequestException("Pick a day between " + startDate + " and today.");
        }
        repository.deleteAll(repository.findAllByModuleAndClosedDateGreaterThanEqual(module, from));
        invalidate();
        return status(module);
    }

    @Audited(module = Mod.ADMIN, action = "Close Day", detail = "#module + ' / ' + #date")
    @Transactional
    public DayStatusResponse close(Mod module, LocalDate date, boolean confirmNothingElse, AuthenticatedPrincipal principal) {
        Role owner = OWNER.get(module);
        if (owner == null) {
            throw new BadRequestException("Days are not closed for this module.");
        }
        boolean allowed = principal.role() == owner || (principal.extraRoles() != null && principal.extraRoles().contains(owner));
        if (!allowed) {
            throw new ForbiddenException("Only the " + owner.label() + " can close a day for this module.");
        }
        LocalDate today = LocalDate.now();
        if (date.isAfter(today)) {
            throw new BadRequestException("You can't close a day that hasn't started.");
        }
        LocalDate working = firstUnclosed(module);
        if (working == null || !working.equals(date)) {
            throw new BadRequestException(working == null
                    ? "That day is already closed."
                    : "Close days in order — the next day to close is " + working + ".");
        }
        if (!confirmNothingElse) {
            java.util.List<String> missing = missingFor(module, date);
            if (!missing.isEmpty()) {
                throw new BadRequestException("Before closing " + date + " please enter: " + String.join("; ", missing)
                        + ". If nothing happened on that day, confirm it and close anyway.");
            }
        }
        repository.save(DayClosure.builder().module(module).closedDate(date).closedBy(principal.username()).build());
        invalidate();
        return status(module);
    }

    /** Used by the system reset. */
    @Transactional
    public void wipe() {
        repository.deleteAllInBatch();
        invalidate();
    }
}
