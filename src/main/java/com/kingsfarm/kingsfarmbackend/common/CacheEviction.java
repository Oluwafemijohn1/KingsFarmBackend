package com.kingsfarm.kingsfarmbackend.common;

import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Clears a cache only AFTER the surrounding database transaction commits.
 * Evicting before the commit would let a concurrent request re-fill the cache
 * with the old row and keep serving it (stale data); evicting after the
 * commit guarantees the next read loads the new values.
 */
@Component
public class CacheEviction {

    private final CacheManager cacheManager;

    public CacheEviction(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void clearAfterCommit(String cacheName) {
        Runnable clear = () -> {
            var cache = cacheManager.getCache(cacheName);
            if (cache != null) cache.clear();
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    clear.run();
                }
            });
        } else {
            clear.run();
        }
    }
}
