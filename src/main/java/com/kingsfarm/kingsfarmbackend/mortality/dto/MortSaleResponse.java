package com.kingsfarm.kingsfarmbackend.mortality.dto;

import com.kingsfarm.kingsfarmbackend.common.PaymentMethod;
import com.kingsfarm.kingsfarmbackend.mortality.MortCat;
import com.kingsfarm.kingsfarmbackend.mortality.MortSaleEntry;

import java.time.Instant;
import java.util.Set;

public record MortSaleResponse(
        Long id,
        MortCat category,
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
    public static MortSaleResponse from(MortSaleEntry e, boolean editable) {
        return new MortSaleResponse(
                e.getId(), e.getCategory(), e.getCustomer(), e.getState(), e.getQty(), e.getPrice(),
                (long) e.getQty() * e.getPrice(), e.getPaymentMethods(), e.getBank(), e.getCashAmount(),
                e.getTransferAmount(), e.getAmountPaid(), e.getCredit(), e.getAdvance(), e.getOccurredAt(),
                e.getEnteredBy(), e.getUpdatedBy(), editable
        );
    }
}
