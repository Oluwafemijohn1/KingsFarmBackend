package com.kingsfarm.kingsfarmbackend.wholeegg.dto;

import com.kingsfarm.kingsfarmbackend.common.CatKey;
import jakarta.validation.constraints.NotNull;

/** One size's quantity in a past-day adjustment. Negative takes away something entered by mistake. */
public record PastQtyEntry(
        @NotNull CatKey category,
        double qty
) {
}
