package com.kingsfarm.kingsfarmbackend.mortality.dto;

import com.kingsfarm.kingsfarmbackend.mortality.MortCat;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateMortPenEntryRequest(
        @NotNull MortCat category,
        @Min(0) int qty
) {
}
