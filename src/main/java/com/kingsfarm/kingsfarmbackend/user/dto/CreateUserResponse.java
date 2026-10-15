package com.kingsfarm.kingsfarmbackend.user.dto;

import com.kingsfarm.kingsfarmbackend.user.Role;

/**
 * No password is ever returned: the new user receives a verification email
 * and sets their own password from the link. {@code emailSent} is false when
 * SMTP is not configured or delivery failed (the link is then in the server log).
 */
public record CreateUserResponse(
        Long id,
        String username,
        String fullName,
        Role role,
        String roleLabel,
        boolean emailSent
) {
}
