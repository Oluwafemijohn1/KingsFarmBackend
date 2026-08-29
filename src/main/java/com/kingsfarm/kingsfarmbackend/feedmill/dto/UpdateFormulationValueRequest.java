package com.kingsfarm.kingsfarmbackend.feedmill.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Value is always in the ingredient's own base unit (kg/g display conversion happens only in the frontend). */
public record UpdateFormulationValueRequest(
        @NotNull Long ingredientId,
        @PositiveOrZero double qtyPerTon
) {
}
