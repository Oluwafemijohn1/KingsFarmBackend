package com.kingsfarm.kingsfarmbackend.crackegg.dto;

import com.kingsfarm.kingsfarmbackend.common.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * credit/advance are hand-typed by staff — never derived server-side, matching
 * CESaleTxn's "not calculated by the system." qty is a double, not int/@Min(1)
 * — a sale can be a fractional crate (0.5, 1.5, etc); "exclusive" 0 keeps the
 * same "must sell something positive" intent @Min(1) had.
 */
public record CreateGcSaleRequest(
        @NotBlank String customer,
        @NotBlank String state,
        @DecimalMin(value = "0", inclusive = false) double qty,
        @Min(0) long price,
        @NotEmpty List<PaymentMethod> paymentMethods,
        String bank,
        @Min(0) long cashAmount,
        @Min(0) long transferAmount,
        @Min(0) long credit,
        @Min(0) long advance
) {
}
