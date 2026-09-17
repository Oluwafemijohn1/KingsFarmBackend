package com.kingsfarm.kingsfarmbackend.settings.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateUnitRequest(@NotBlank String name) {
}
