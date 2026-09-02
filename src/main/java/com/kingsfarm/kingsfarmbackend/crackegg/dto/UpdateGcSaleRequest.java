package com.kingsfarm.kingsfarmbackend.crackegg.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** Only customer/state/qty/price/credit/advance are editable in place — matches GcSaleDraft in CrackEggView.tsx (payment method, bank, and amounts stay fixed once made). */
public record UpdateGcSaleRequest(
        @NotBlank String customer,
        @NotBlank String state,
        @Min(1) int qty,
        @Min(0) long price,
        @Min(0) long credit,
        @Min(0) long advance
) {
}
