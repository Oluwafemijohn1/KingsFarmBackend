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

    /**
     * No row means this scope has never been touched — it must be EDITABLE so a manager can
     * enter the very first Opening Stock value. Once that first value is saved, the module
     * service calls lock() explicitly, which is what actually locks it going forward (see
     * setOpening()/lockOpening() in each module's service). An explicitly re-locked row means
     * "locked" until an Administrator approves an OpeningStockRequest (unlock()).
     */
    @Transactional(readOnly = true)
    public boolean isLocked(Mod module, String scope) {
        return repository.findByModuleAndScope(module, scope)
                .map(state -> !state.isUnlocked())
                .orElse(false);
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
