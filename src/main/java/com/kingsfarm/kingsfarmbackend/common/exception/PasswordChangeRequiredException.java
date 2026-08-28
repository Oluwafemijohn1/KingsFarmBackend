package com.kingsfarm.kingsfarmbackend.common.exception;

/**
 * Thrown by the must-change-password gate (see
 * security.MustChangePasswordFilter) for every request from an account that
 * still has {@code mustChangePassword = true}, except the two endpoints that
 * are allowed to fire regardless: /api/v1/auth/change-password and
 * /api/v1/auth/logout. Mapped to a distinct "PASSWORD_CHANGE_REQUIRED" error
 * code (not just a 403 message) so the frontend can reliably redirect to the
 * change-password screen instead of string-matching an error message.
 */
public class PasswordChangeRequiredException extends RuntimeException {
    public PasswordChangeRequiredException() {
        super("This account must change its password before doing anything else.");
    }
}
