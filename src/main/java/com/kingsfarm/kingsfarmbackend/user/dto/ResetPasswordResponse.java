package com.kingsfarm.kingsfarmbackend.user.dto;

/** Returned once by the admin "reset password" action — same one-time-reveal contract as CreateUserResponse. */
public record ResetPasswordResponse(Long userId, String username, String generatedPassword) {
}
