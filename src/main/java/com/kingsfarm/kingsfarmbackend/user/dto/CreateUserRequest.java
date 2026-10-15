package com.kingsfarm.kingsfarmbackend.user.dto;

import com.kingsfarm.kingsfarmbackend.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code username} is the user's real email address - it is also their login ID. */
public record CreateUserRequest(
        @NotBlank String fullName,
        @NotBlank @Email(message = "must be a valid email address") @Size(max = 64, message = "must be at most 64 characters") String username,
        @NotNull Role role
) {
}
