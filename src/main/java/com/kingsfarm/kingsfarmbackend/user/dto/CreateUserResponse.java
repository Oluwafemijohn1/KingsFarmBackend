package com.kingsfarm.kingsfarmbackend.user.dto;

import com.kingsfarm.kingsfarmbackend.user.Role;

/**
 * The one and only time the plain-text generated password is ever returned.
 * The admin relays {@code generatedPassword} to the new user out of band;
 * neither this value nor anything derivable from it is retrievable again.
 */
public record CreateUserResponse(
        Long id,
        String username,
        String fullName,
        Role role,
        String roleLabel,
        String generatedPassword
) {
}
