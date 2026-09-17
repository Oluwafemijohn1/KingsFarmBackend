package com.kingsfarm.kingsfarmbackend.wholeegg.dto;

import com.kingsfarm.kingsfarmbackend.wholeegg.VerificationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body for the Payment Auditing tick/cross toggle — remark is optional (only needed when flagging a discrepancy). */
public record VerifyPaymentRequest(
        @NotNull VerificationStatus status,
        @Size(max = 500) String remark
) {
}
