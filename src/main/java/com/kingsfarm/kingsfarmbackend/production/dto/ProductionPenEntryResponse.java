package com.kingsfarm.kingsfarmbackend.production.dto;

import com.kingsfarm.kingsfarmbackend.common.CatKey;
import com.kingsfarm.kingsfarmbackend.production.ProductionPenEntry;

import java.time.LocalDate;
import java.util.Map;

public record ProductionPenEntryResponse(
        Long id,
        Long penId,
        String penName,
        LocalDate entryDate,
        Map<CatKey, Integer> qty,
        int total,
        int birdClosing,
        String productionPercent,
        String enteredBy,
        String updatedBy,
        boolean editable
) {
    public static ProductionPenEntryResponse from(ProductionPenEntry entry, int birdClosing, String productionPercent, boolean editable) {
        return new ProductionPenEntryResponse(
                entry.getId(), entry.getPen().getId(), entry.getPen().getName(), entry.getEntryDate(),
                entry.asMap(), entry.total(), birdClosing, productionPercent,
                entry.getEnteredBy(), entry.getUpdatedBy(), editable
        );
    }
}
