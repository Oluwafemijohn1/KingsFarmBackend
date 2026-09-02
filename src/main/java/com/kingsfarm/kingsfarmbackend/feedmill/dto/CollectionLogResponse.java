package com.kingsfarm.kingsfarmbackend.feedmill.dto;

import com.kingsfarm.kingsfarmbackend.feedmill.FeedCollectionLogEntry;

import java.time.Instant;

public record CollectionLogResponse(
        Long id,
        String collectedBy,
        double fishStarterKg,
        double fishGrowerKg,
        double fishFinisherKg,
        double total,
        Instant occurredAt,
        String enteredBy,
        String updatedBy,
        boolean editable
) {
    public static CollectionLogResponse from(FeedCollectionLogEntry e, boolean editable) {
        return new CollectionLogResponse(
                e.getId(), e.getCollectedBy().wire(), e.getFishStarterKg(), e.getFishGrowerKg(), e.getFishFinisherKg(),
                e.total(), e.getOccurredAt(), e.getEnteredBy(), e.getUpdatedBy(), editable
        );
    }
}
