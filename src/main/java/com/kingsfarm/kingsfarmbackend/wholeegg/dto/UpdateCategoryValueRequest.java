package com.kingsfarm.kingsfarmbackend.wholeegg.dto;

import com.kingsfarm.kingsfarmbackend.common.CatKey;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** Shared by both Opening Stock (fractional crates) and Price (always whole Naira in practice) updates. */
public record UpdateCategoryValueRequest(
        @NotNull CatKey category,
        @DecimalMin("0") double value,
        /** Opening stock only: the (past) working day the value is for; null = today's live opening. */
        java.time.LocalDate date
) {
}
