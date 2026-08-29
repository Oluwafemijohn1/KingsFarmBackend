package com.kingsfarm.kingsfarmbackend.mortality.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** One atomic "Save Gifts" action — commits Good/Dry/Runt gift totals AND appends a log entry in one call, matching MortalityView.tsx's saveGifts() exactly (the form has no independent live-save per field, unlike Crack Egg's gift fields). */
public record SaveGiftRequest(
        @Min(0) int good,
        @Min(0) int dry,
        @Min(0) int runt,
        @NotBlank String recipient,
        @NotBlank String authorizer
) {
}
