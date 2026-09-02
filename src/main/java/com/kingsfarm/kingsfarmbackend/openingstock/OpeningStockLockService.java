package com.kingsfarm.kingsfarmbackend.openingstock;

import com.kingsfarm.kingsfarmbackend.common.Mod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every Phase 3 module service calls this instead of rolling its own
 * opening-stock lock flag. Mirrors the frontend's isOpeningLocked /
 * lockOpeningField / (approval → unlock) trio exactly (see shared.ts's
 * "OPENING STOCK LOCKING" comment block).
 */
@Service
public class OpeningStockLockService {

    private final OpeningStockStateRepository repository;

    public OpeningStockLockService(OpeningStockStateRepository repository) {
        this.repository = repository;
    }

    /** No row (never touched) or an explicitly re-locked row both mean "locked" — matches the frontend's `!openingUnlocked[key]` default. */
    @Transactional(readOnly = true)
    public boolean isLocked(Mod module, String scope) {
        return repository.findByModuleAndScope(module, scope)
                .map(state -> !state.isUnlocked())
                .orElse(true);
    }

    /** Called once a manager finishes correcting an unlocked field, so it goes back to being carried-forward/locked for the next entry. */
    @Transactional
    public void lock(Mod module, String scope) {
        setUnlocked(module, scope, false);
    }

    /** Called when an Administrator approves an OpeningStockRequest for this field. */
    @Transactional
    public void unlock(Mod module, String scope) {
        setUnlocked(module, scope, true);
    }

    private void setUnlocked(Mod module, String scope, boolean unlocked) {
        OpeningStockState state = repository.findByModuleAndScope(module, scope)
                .orElseGet(() -> {
                    OpeningStockState s = new OpeningStockState();
                    s.setModule(module);
                    s.setScope(scope);
                    return s;
                });
        state.setUnlocked(unlocked);
        repository.save(state);
    }
}
