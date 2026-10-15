package com.kingsfarm.kingsfarmbackend.user.dto;

/** Returned by the admin "reset password" / "resend invitation" actions - the user gets an emailed link; no password is revealed. */
public record ResetPasswordResponse(Long userId, String username, boolean emailSent) {
}
