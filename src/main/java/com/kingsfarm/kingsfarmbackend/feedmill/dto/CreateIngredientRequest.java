package com.kingsfarm.kingsfarmbackend.feedmill.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record CreateIngredientRequest(
        @NotBlank String name,
        @NotBlank String unit,
        @PositiveOrZero double opening,
        @PositiveOrZero double min
) {
}
