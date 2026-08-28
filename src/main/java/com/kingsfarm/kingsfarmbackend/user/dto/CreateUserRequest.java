package com.kingsfarm.kingsfarmbackend.user.dto;

import com.kingsfarm.kingsfarmbackend.user.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateUserRequest(
        @NotBlank String fullName,
        @NotBlank @Pattern(regexp = "^[a-zA-Z0-9._-]{3,64}$", message = "must be 3-64 characters: letters, digits, dot, underscore, or hyphen") String username,
        @NotNull Role role
) {
}
