package com.kingsfarm.kingsfarmbackend.mortality.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** Only customer/state/qty/price/credit/advance are editable in place — category, time, and payment method stay fixed once made, per the frontend's MortSaleDraft type. */
public record UpdateMortSaleRequest(
        @NotBlank String customer,
        @NotBlank String state,
        @Min(1) int qty,
        @Min(0) long price,
        @Min(0) long credit,
        @Min(0) long advance
) {
}
