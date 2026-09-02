package com.kingsfarm.kingsfarmbackend.mortality.dto;

import com.kingsfarm.kingsfarmbackend.common.PaymentMethod;
import com.kingsfarm.kingsfarmbackend.mortality.MortCat;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/** credit/advance are hand-typed by staff — never derived server-side, same as Crack Egg. */
public record CreateMortSaleRequest(
        @NotNull MortCat category,
        @NotBlank String customer,
        @NotBlank String state,
        @Min(1) int qty,
        @Min(0) long price,
        @NotEmpty java.util.List<PaymentMethod> paymentMethods,
        String bank,
        @Min(0) long cashAmount,
        @Min(0) long transferAmount,
        @Min(0) long credit,
        @Min(0) long advance
) {
}
