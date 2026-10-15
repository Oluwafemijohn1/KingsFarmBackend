package com.kingsfarm.kingsfarmbackend.settings;

import com.kingsfarm.kingsfarmbackend.audit.Audited;
import com.kingsfarm.kingsfarmbackend.common.Mod;
import com.kingsfarm.kingsfarmbackend.common.exception.BadRequestException;
import com.kingsfarm.kingsfarmbackend.common.exception.ConflictException;
import com.kingsfarm.kingsfarmbackend.common.exception.NotFoundException;
import com.kingsfarm.kingsfarmbackend.settings.dto.CreateUnitRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Units of Measurement (Admin → System Configuration) — a small,
 * Administrator-owned reference catalog, same shape as Feed Mill's
 * {@code FeedType} (dedupe case-insensitively, no soft-deactivate) but with
 * a real delete, since nothing else in the app references a Unit by id yet.
 */
@Service
public class UnitService {

    private final UnitRepository repository;

    public UnitService(UnitRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Unit> list() {
        return repository.findAllByOrderByIdAsc();
    }

    @Audited(module = Mod.ADMIN, action = "Add Unit of Measurement", detail = "#request.name()")
    @Transactional
    public Unit create(CreateUnitRequest request) {
        String name = request.name().trim();
        if (name.isEmpty()) {
            throw new BadRequestException("Enter a unit name.");
        }
        if (repository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("This unit already exists.");
        }
        return repository.save(Unit.builder().name(name).build());
    }

    @Audited(module = Mod.ADMIN, action = "Remove Unit of Measurement", detail = "'Unit #' + #id")
    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new NotFoundException("Unit not found.");
        }
        repository.deleteById(id);
    }
}
