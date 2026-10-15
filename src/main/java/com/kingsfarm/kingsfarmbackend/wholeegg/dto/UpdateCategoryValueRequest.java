package com.kingsfarm.kingsfarmbackend.wholeegg.dto;

import com.kingsfarm.kingsfarmbackend.common.CatKey;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** Shared by both Opening Stock (fractional crates) and Price (always whole Naira in practice) updates. */
public record UpdateCategoryValueRequest(
        @NotNull CatKey category,
        @DecimalMin("0") double value
) {
}
