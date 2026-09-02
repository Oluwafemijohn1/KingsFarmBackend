package com.kingsfarm.kingsfarmbackend.feedmill.dto;

public record ProductionSummaryRow(
        Long feedTypeId,
        String feedTypeName,
        double totalTons,
        int runs
) {
}
