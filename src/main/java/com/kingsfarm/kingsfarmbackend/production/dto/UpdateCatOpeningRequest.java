package com.kingsfarm.kingsfarmbackend.production.dto;

import com.kingsfarm.kingsfarmbackend.common.CatKey;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** double, not int/@Min — Category Stock Summary's Opening Stock can now be a fractional crate count, same as Production by Pen's entries that carry forward into it. */
public record UpdateCatOpeningRequest(
        @NotNull CatKey category,
        @DecimalMin("0") double value
) {
}
