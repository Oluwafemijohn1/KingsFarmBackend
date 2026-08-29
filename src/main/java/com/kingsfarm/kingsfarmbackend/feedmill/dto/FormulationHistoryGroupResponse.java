package com.kingsfarm.kingsfarmbackend.feedmill.dto;

import com.kingsfarm.kingsfarmbackend.feedmill.FeedFormulationHistoryGroup;

import java.time.Instant;
import java.util.List;

public record FormulationHistoryGroupResponse(
        Long id,
        String feedTypeName,
        String changedBy,
        Instant occurredAt,
        List<FormulationHistoryItemResponse> changes
) {
    public static FormulationHistoryGroupResponse from(FeedFormulationHistoryGroup g, List<FormulationHistoryItemResponse> changes) {
        return new FormulationHistoryGroupResponse(g.getId(), g.getFeedType().getName(), g.getChangedBy(), g.getOccurredAt(), changes);
    }
}
