package com.kingsfarm.kingsfarmbackend.wholeegg.dto;

import com.kingsfarm.kingsfarmbackend.common.PaymentMethod;
import com.kingsfarm.kingsfarmbackend.wholeegg.WeSaleTxnType;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record WeSaleTransactionResponse(
        Long id,
        Long customerId,
        String customerName,
        String state,
        WeSaleTxnType type,
        Instant occurredAt,
        int txnYear,
        List<SaleLineItemResponse> items,
        long revenue,
        Set<PaymentMethod> paymentMethods,
        String bank,
        long cashAmount,
        long transferAmount,
        long amountPaid,
        long credit,
        long advance,
        String enteredBy,
        String updatedBy,
        boolean editable,
        /** The customer's balance immediately before this transaction (priorBalanceFor in WholeEggView) — positive = they owed, negative = they had an advance. */
        long priorBalance
) {
}
