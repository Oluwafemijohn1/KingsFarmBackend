package com.kingsfarm.kingsfarmbackend.wholeegg.dto;

import com.kingsfarm.kingsfarmbackend.common.CatKey;

import java.time.Instant;
import java.time.LocalDate;

/** One saved gift / sales-crack line. {@code qty} is negative when crates were taken back. */
public record AdjustmentLogResponse(
        Long id,
        LocalDate day,
        String kind,
        CatKey category,
        double qty,
        String enteredBy,
        Instant occurredAt
) {
}
