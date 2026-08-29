package com.kingsfarm.kingsfarmbackend.wholeegg.dto;

import com.kingsfarm.kingsfarmbackend.common.CatKey;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CategoryQtyEntry(
        @NotNull CatKey category,
        @Min(0) int qty
) {
}
