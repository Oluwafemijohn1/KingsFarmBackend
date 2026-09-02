package com.kingsfarm.kingsfarmbackend.production.dto;

import com.kingsfarm.kingsfarmbackend.common.CatKey;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** One cell at a time — matches the frontend's setPenProdCell(pen, category, value). */
public record UpdatePenEntryRequest(
        @NotNull CatKey category,
        @Min(0) int qty
) {
}
