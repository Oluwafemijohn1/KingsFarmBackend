package com.kingsfarm.kingsfarmbackend.wholeegg.dto;

import com.kingsfarm.kingsfarmbackend.common.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Applies uniformly to sale/payment/opening rows, matching the frontend's single shared edit modal — items is empty for payment/opening (they have none), which is fine since totalDue is then zero. */
public record UpdateSaleTxnRequest(
        @Valid List<SaleLineItemRequest> items,
        @NotEmpty List<PaymentMethod> paymentMethods,
        String bank,
        @Min(0) long cashAmount,
        @Min(0) long transferAmount
) {
}
