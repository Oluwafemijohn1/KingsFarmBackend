package com.kingsfarm.kingsfarmbackend.mortality.dto;

import com.kingsfarm.kingsfarmbackend.mortality.MortGiftLogEntry;

import java.time.Instant;

public record GiftLogEntryResponse(
        Long id,
        int good,
        int dry,
        int runt,
        int total,
        String recipient,
        String authorizer,
        Instant occurredAt,
        String enteredBy,
        String updatedBy,
        boolean editable
) {
    public static GiftLogEntryResponse from(MortGiftLogEntry e, boolean editable) {
        return new GiftLogEntryResponse(
                e.getId(), e.getGood(), e.getDry(), e.getRunt(), e.getGood() + e.getDry() + e.getRunt(),
                e.getRecipient(), e.getAuthorizer(), e.getOccurredAt(), e.getEnteredBy(), e.getUpdatedBy(), editable
        );
    }
}
