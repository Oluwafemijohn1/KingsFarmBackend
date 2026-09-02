package com.kingsfarm.kingsfarmbackend.common.exception;

/** Too many failed login attempts within the lockout window (see SecuritySettings.lockoutAttempts). */
public class AccountLockedException extends RuntimeException {
    public AccountLockedException(String message) {
        super(message);
    }
}
