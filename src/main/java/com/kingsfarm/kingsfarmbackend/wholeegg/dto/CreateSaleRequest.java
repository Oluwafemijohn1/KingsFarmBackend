package com.kingsfarm.kingsfarmbackend.wholeegg.dto;

import com.kingsfarm.kingsfarmbackend.common.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** amountPaid is never sent — the server always derives it from cashAmount/transferAmount vs. which payment methods are selected, same as the frontend's "effective" amounts. */
public record CreateSaleRequest(
        @NotNull Long customerId,
        @NotEmpty @Valid List<SaleLineItemRequest> items,
        @NotEmpty List<PaymentMethod> paymentMethods,
        String bank,
        @Min(0) long cashAmount,
        @Min(0) long transferAmount,
        /** Multi-bank transfers. When present (and Transfer is selected) this wins over {@code bank}/{@code transferAmount}: transferAmount is the sum of these. */
        @jakarta.validation.Valid List<BankTransferRequest> transfers
) {
}
