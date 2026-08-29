package com.kingsfarm.kingsfarmbackend.feedmill.dto;

import com.kingsfarm.kingsfarmbackend.feedmill.FeedProductionLogEntry;

import java.time.Instant;

public record ProductionLogResponse(
        Long id,
        Long feedTypeId,
        String feedTypeName,
        double qtyTons,
        double qtyKg,
        String formulationRef,
        Instant occurredAt,
        String enteredBy,
        String updatedBy,
        boolean editable
) {
    public static ProductionLogResponse from(FeedProductionLogEntry e, boolean editable) {
        return new ProductionLogResponse(
                e.getId(), e.getFeedType().getId(), e.getFeedType().getName(), e.getQtyTons(), e.getQtyTons() * 1000,
                e.getFormulationRef(), e.getOccurredAt(), e.getEnteredBy(), e.getUpdatedBy(), editable
        );
    }
}
