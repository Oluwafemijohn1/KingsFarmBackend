package com.kingsfarm.kingsfarmbackend.crackegg.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** A Good Crack gift or a Rough Crack feed-mill transfer for a finished day — the quantity is added to that day. */
public record PastCrackAdjustmentRequest(
        @NotNull LocalDate date,
        @NotNull Kind kind,
        double qty
) {
    public enum Kind { GOOD_GIFT, ROUGH_FEED_MILL }
}
