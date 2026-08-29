package com.kingsfarm.kingsfarmbackend.feedmill.dto;

import java.util.List;

/**
 * status/targetKg/toleranceKg mirror {@code formulationStatus}/
 * {@code FORMULATION_TARGET_KG}/{@code FORMULATION_TOLERANCE_KG} — purely
 * informational, never blocks {@code saveFormulation} (the frontend never
 * disables its Save button based on this, unlike Feed Production's hard
 * stock-availability block).
 */
public record FormulationResponse(
        Long feedTypeId,
        String feedTypeName,
        List<FormulationEntryResponse> entries,
        double totalKg,
        double targetKg,
        double toleranceKg,
        String status
) {
}
