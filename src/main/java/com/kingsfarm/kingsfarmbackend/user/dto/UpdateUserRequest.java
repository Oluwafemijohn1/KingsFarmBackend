package com.kingsfarm.kingsfarmbackend.user.dto;

import com.kingsfarm.kingsfarmbackend.user.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRequest(
        @NotBlank String fullName,
        @NotNull Role role
) {
}
