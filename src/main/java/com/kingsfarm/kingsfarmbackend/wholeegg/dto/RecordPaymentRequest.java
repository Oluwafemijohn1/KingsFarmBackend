package com.kingsfarm.kingsfarmbackend.wholeegg.dto;

import com.kingsfarm.kingsfarmbackend.common.PaymentMethod;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record RecordPaymentRequest(
        @NotNull Long customerId,
        @NotEmpty List<PaymentMethod> paymentMethods,
        String bank,
        @Min(0) long cashAmount,
        @Min(0) long transferAmount,
        /** Multi-bank transfers. When present (and Transfer is selected) this wins over {@code bank}/{@code transferAmount}: transferAmount is the sum of these. */
        @jakarta.validation.Valid List<BankTransferRequest> transfers
) {
}
