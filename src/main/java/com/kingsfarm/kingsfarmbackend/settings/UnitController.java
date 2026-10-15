package com.kingsfarm.kingsfarmbackend.settings;

import com.kingsfarm.kingsfarmbackend.settings.dto.CreateUnitRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Administrator owns removal, but adding is shared: Feed Mill Manager can
 * add a unit too (in case something they need isn't in the list yet — they
 * hit this via the inline "+" next to the unit picker in FeedMillView.tsx's
 * Add Ingredient form, not the Admin config page, which they don't have
 * access to). Feed Mill Manager deliberately cannot delete — this catalog
 * still has one owner for removal, same as the read-broadly/write-narrowly
 * shape FeedMillController itself uses. Widen the class-level (read) or
 * create()'s role list here if another module needs to read or add too.
 */
@RestController
@RequestMapping("/api/v1/admin/units")
@PreAuthorize("hasAnyRole('ADMINISTRATOR', 'MANAGING_DIRECTOR', 'FEED_MILL_MANAGER')")
public class UnitController {

    private final UnitService service;

    public UnitController(UnitService service) {
        this.service = service;
    }

    @GetMapping
    public List<Unit> list() {
        return service.list();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'FEED_MILL_MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public Unit create(@Valid @RequestBody CreateUnitRequest request) {
        return service.create(request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
