package com.kingsfarm.kingsfarmbackend.feedmill.dto;

import java.time.Instant;
import java.time.LocalDate;

public record IngredientReceiptResponse(
        Long id,
        String ingredient,
        String unit,
        LocalDate day,
        double qty,
        String enteredBy,
        Instant occurredAt,
        String note
) {
}
