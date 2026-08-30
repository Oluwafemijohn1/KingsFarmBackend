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
        List<Role> extraRoles
) {
}
