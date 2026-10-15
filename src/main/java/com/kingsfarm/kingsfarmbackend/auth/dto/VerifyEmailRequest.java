package com.kingsfarm.kingsfarmbackend.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyEmailRequest(@NotBlank String token, @NotBlank String password) {
}
