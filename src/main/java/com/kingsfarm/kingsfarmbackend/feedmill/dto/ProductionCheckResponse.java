package com.kingsfarm.kingsfarmbackend.feedmill.dto;

import java.util.List;

public record ProductionCheckResponse(
        Long feedTypeId,
        String feedTypeName,
        double qtyTons,
        double qtyKg,
        List<RequirementRow> requirements,
        boolean canProduce
) {
}
