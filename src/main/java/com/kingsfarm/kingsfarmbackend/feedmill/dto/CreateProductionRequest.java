package com.kingsfarm.kingsfarmbackend.feedmill.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateProductionRequest(
        @NotNull Long feedTypeId,
        @Positive double qtyTons
) {
}
