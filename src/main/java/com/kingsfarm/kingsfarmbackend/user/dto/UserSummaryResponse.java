package com.kingsfarm.kingsfarmbackend.user.dto;

import com.kingsfarm.kingsfarmbackend.user.Role;
import com.kingsfarm.kingsfarmbackend.user.User;

import java.time.Instant;

/** Never includes passwordHash — this is what the admin Users table renders from. */
public record UserSummaryResponse(
        Long id,
        String username,
        String fullName,
        Role role,
        String roleLabel,
        boolean active,
        boolean mustChangePassword,
        Instant createdAt,
        String createdBy,
        Instant lastLoginAt
) {
    public static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(
                user.getId(), user.getUsername(), user.getFullName(),
                user.getRole(), user.getRole().label(),
                user.isActive(), user.isMustChangePassword(),
                user.getCreatedAt(), user.getCreatedBy(), user.getLastLoginAt()
        );
    }
}
