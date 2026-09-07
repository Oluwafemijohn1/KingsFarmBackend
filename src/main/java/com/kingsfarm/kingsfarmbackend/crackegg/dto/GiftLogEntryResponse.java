package com.kingsfarm.kingsfarmbackend.crackegg.dto;

import com.kingsfarm.kingsfarmbackend.crackegg.CrackEggGiftLogEntry;

import java.time.Instant;

public record GiftLogEntryResponse(
        Long id,
        double qty,
        String recipient,
        String authorizer,
        Instant occurredAt,
        String enteredBy,
        String updatedBy,
        boolean editable
) {
    public static GiftLogEntryResponse from(CrackEggGiftLogEntry e, boolean editable) {
        return new GiftLogEntryResponse(e.getId(), e.getQty(), e.getRecipient(), e.getAuthorizer(), e.getOccurredAt(), e.getEnteredBy(), e.getUpdatedBy(), editable);
    }
}
