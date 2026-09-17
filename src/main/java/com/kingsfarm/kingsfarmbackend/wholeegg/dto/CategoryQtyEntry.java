package com.kingsfarm.kingsfarmbackend.wholeegg.dto;

import com.kingsfarm.kingsfarmbackend.common.CatKey;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record CategoryQtyEntry(
        @NotNull CatKey category,
        @DecimalMin("0") double qty
) {
}
