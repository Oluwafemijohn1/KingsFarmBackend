package com.kingsfarm.kingsfarmbackend.settings;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lazily creates the singleton settings row with defaults on first read, so
 * there's no separate migration/seed step just for this one row.
 */
@Service
public class SecuritySettingsService {

    private final SecuritySettingsRepository repository;

    public SecuritySettingsService(SecuritySettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public SecuritySettings get() {
        return repository.findById(SecuritySettings.SINGLETON_ID)
                .orElseGet(() -> repository.save(new SecuritySettings()));
    }

    @Transactional
    public SecuritySettings update(int sessionTimeoutMinutes, int lockoutAttempts, int passwordMinLength) {
        SecuritySettings settings = get();
        settings.setSessionTimeoutMinutes(sessionTimeoutMinutes);
        settings.setLockoutAttempts(lockoutAttempts);
        settings.setPasswordMinLength(passwordMinLength);
        return repository.save(settings);
    }
}
