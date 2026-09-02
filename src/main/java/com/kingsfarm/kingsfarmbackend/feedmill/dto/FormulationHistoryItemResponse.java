package com.kingsfarm.kingsfarmbackend.feedmill.dto;

import com.kingsfarm.kingsfarmbackend.feedmill.FeedFormulationHistoryItem;

public record FormulationHistoryItemResponse(
        Long ingredientId,
        String ingredientName,
        double oldVal,
        double newVal
) {
    public static FormulationHistoryItemResponse from(FeedFormulationHistoryItem i) {
        return new FormulationHistoryItemResponse(i.getIngredient().getId(), i.getIngredient().getName(), i.getOldVal(), i.getNewVal());
    }
}
