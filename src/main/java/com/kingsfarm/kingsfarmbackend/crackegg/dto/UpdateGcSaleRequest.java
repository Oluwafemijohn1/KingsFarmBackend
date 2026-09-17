package com.kingsfarm.kingsfarmbackend.crackegg.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** Only customer/state/qty/price/credit/advance are editable in place — matches GcSaleDraft in CrackEggView.tsx (payment method, bank, and amounts stay fixed once made). qty is a double — see CreateGcSaleRequest's javadoc. */
public record UpdateGcSaleRequest(
        @NotBlank String customer,
        @NotBlank String state,
        @DecimalMin(value = "0", inclusive = false) double qty,
        @Min(0) long price,
        @Min(0) long credit,
        @Min(0) long advance
) {
}
