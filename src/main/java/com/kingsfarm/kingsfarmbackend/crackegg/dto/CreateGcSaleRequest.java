package com.kingsfarm.kingsfarmbackend.crackegg.dto;

import com.kingsfarm.kingsfarmbackend.common.PaymentMethod;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** credit/advance are hand-typed by staff — never derived server-side, matching CESaleTxn's "not calculated by the system." */
public record CreateGcSaleRequest(
        @NotBlank String customer,
        @NotBlank String state,
        @Min(1) int qty,
        @Min(0) long price,
        @NotEmpty List<PaymentMethod> paymentMethods,
        String bank,
        @Min(0) long cashAmount,
        @Min(0) long transferAmount,
        @Min(0) long credit,
        @Min(0) long advance
) {
}
