package com.kingsfarm.kingsfarmbackend.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.kingsfarm.kingsfarmbackend.user.Role;

import java.util.List;

/**
 * {@code accessToken}/{@code refreshToken} exist so {@link com.kingsfarm.kingsfarmbackend.auth.AuthController}
 * can read them to set the httpOnly cookies (BACKEND_PLAN.md §11 decision #2)
 * — they're deliberately excluded from the JSON body itself via
 * {@code @JsonIgnoreProperties}, since the whole point of an httpOnly cookie
 * is that JS (including the response-handling code that reads this body)
 * never sees the raw token.
 */
@JsonIgnoreProperties({"accessToken", "refreshToken"})
public record LoginResponse(
        String accessToken,
        String refreshToken,
        Long userId,
        String username,
        String fullName,
        Role role,
        String roleLabel,
        boolean mustChangePassword,
        /** Roles this user currently covers via an active Relief Access grant — additive to {@code role}, see ReliefGrant's javadoc. */
        List<Role> extraRoles,
        /**
         * The admin-configured idle-logout threshold (SecuritySettings.sessionTimeoutMinutes),
         * surfaced here rather than only via the Administrator-only
         * {@code /api/v1/admin/security-settings} endpoint so every signed-in user's browser
         * — not just admins — can size its idle-timeout timer to the real, current value.
         */
        int sessionTimeoutMinutes
) {
}
