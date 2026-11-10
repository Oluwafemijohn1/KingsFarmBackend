package com.kingsfarm.kingsfarmbackend.mortality.dto;

import com.kingsfarm.kingsfarmbackend.mortality.MortCat;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** Customer, state, category, qty, price, credit and advance can be corrected while the day is open; {@code category} null keeps the current one. Time and payment method stay fixed. */
public record UpdateMortSaleRequest(
        @NotBlank String customer,
        @NotBlank String state,
        @Min(1) int qty,
        @Min(0) long price,
        @Min(0) long credit,
        @Min(0) long advance,
        MortCat category
) {
}
