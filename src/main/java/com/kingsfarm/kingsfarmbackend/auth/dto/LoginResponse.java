package com.kingsfarm.kingsfarmbackend.auth.dto;

import com.kingsfarm.kingsfarmbackend.user.Role;

import java.util.List;

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
