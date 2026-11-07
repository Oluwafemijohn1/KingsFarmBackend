package com.kingsfarm.kingsfarmbackend.wholeegg.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/**
 * Crack use or gifts that belong to a day that has already closed. Unlike today's
 * "commit" (which overwrites a running figure), these quantities are ADDED to that
 * day, because a finished day's own figure is no longer kept.
 */
public record PastAdjustmentRequest(
        @NotNull LocalDate date,
        @NotNull Kind kind,
        @NotEmpty @Valid List<PastQtyEntry> values
) {
    public enum Kind { CRACK, GIFT }
}
