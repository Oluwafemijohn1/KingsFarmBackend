package com.kingsfarm.kingsfarmbackend.feedmill.dto;

import com.kingsfarm.kingsfarmbackend.feedmill.FeedIngredient;

public record FeedIngredientResponse(
        Long id,
        String name,
        String unit,
        double opening,
        boolean openingLocked,
        double added,
        double used,
        double min,
        double closing,
        boolean low,
        String enteredBy,
        String updatedBy
) {
    public static FeedIngredientResponse from(FeedIngredient i, boolean openingLocked) {
        return new FeedIngredientResponse(
                i.getId(), i.getName(), i.getUnit(), i.getOpening(), openingLocked, i.getAdded(), i.getUsed(),
                i.getMin(), i.closing(), i.low(), i.getEnteredBy(), i.getUpdatedBy()
        );
    }
}
