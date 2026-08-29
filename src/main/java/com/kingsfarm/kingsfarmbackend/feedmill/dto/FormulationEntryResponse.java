package com.kingsfarm.kingsfarmbackend.feedmill.dto;

import com.kingsfarm.kingsfarmbackend.feedmill.FeedFormulationEntry;

public record FormulationEntryResponse(
        Long ingredientId,
        String ingredientName,
        String unit,
        double qtyPerTon
) {
    public static FormulationEntryResponse from(FeedFormulationEntry e) {
        return new FormulationEntryResponse(
                e.getIngredient().getId(), e.getIngredient().getName(), e.getIngredient().getUnit(), e.getQtyPerTon()
        );
    }

    /** For an ingredient with no formulation row yet — displays as zero, matching {@code formulations[editFormType]?.[ing.name] ?? 0} in the frontend. */
    public static FormulationEntryResponse zero(com.kingsfarm.kingsfarmbackend.feedmill.FeedIngredient ingredient) {
        return new FormulationEntryResponse(ingredient.getId(), ingredient.getName(), ingredient.getUnit(), 0);
    }
}
