package com.kingsfarm.kingsfarmbackend.feedmill.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Only Feed Type and Qty are editable in place, matching the frontend's prodDraft ({ type, qty }). */
public record UpdateProductionRequest(
        @NotNull Long feedTypeId,
        @Positive double qtyTons
) {
}
