package com.kingsfarm.kingsfarmbackend.crackegg.dto;

import com.kingsfarm.kingsfarmbackend.common.PaymentMethod;
import com.kingsfarm.kingsfarmbackend.crackegg.GcSaleTransaction;

import java.time.Instant;
import java.util.Set;

public record GcSaleResponse(
        Long id,
        String customer,
        String state,
        int qty,
        long price,
        long revenue,
        Set<PaymentMethod> paymentMethods,
        String bank,
        long cashAmount,
        long transferAmount,
        long amountPaid,
        long credit,
        long advance,
        Instant occurredAt,
        String enteredBy,
        String updatedBy,
        boolean editable
) {
    public static GcSaleResponse from(GcSaleTransaction t, boolean editable) {
        return new GcSaleResponse(
                t.getId(), t.getCustomer(), t.getState(), t.getQty(), t.getPrice(), (long) t.getQty() * t.getPrice(),
                t.getPaymentMethods(), t.getBank(), t.getCashAmount(), t.getTransferAmount(), t.getAmountPaid(),
                t.getCredit(), t.getAdvance(), t.getOccurredAt(), t.getEnteredBy(), t.getUpdatedBy(), editable
        );
    }
}
