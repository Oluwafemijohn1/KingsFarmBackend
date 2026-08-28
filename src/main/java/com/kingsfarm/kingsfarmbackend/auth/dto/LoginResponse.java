package com.kingsfarm.kingsfarmbackend.auth.dto;

import com.kingsfarm.kingsfarmbackend.user.Role;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        Long userId,
        String username,
        String fullName,
        Role role,
        String roleLabel,
        boolean mustChangePassword
) {
}
