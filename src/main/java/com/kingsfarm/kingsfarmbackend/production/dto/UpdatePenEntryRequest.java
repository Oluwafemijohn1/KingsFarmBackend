package com.kingsfarm.kingsfarmbackend.production.dto;

import com.kingsfarm.kingsfarmbackend.common.CatKey;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * One cell at a time — matches the frontend's setPenProdCell(pen, category, value).
 * qty is a double (not int/@Min — @Min only supports integral types): a
 * pen's production can be a partial crate, e.g. 1.5.
 */
public record UpdatePenEntryRequest(
        @NotNull CatKey category,
        @DecimalMin("0") double qty
) {
}
