package com.kingsfarm.kingsfarmbackend.crackegg.dto;

import jakarta.validation.constraints.DecimalMin;

/**
 * double, not int/@Min — backs Good/Rough Crack Opening Stock, Gift Qty, and Feed Mill usage, all of which can be fractional crates.
 * {@code date}: for an opening stock, the working day it belongs to; null means today.
 */
public record UpdateIntValueRequest(@DecimalMin("0") double value, java.time.LocalDate date) {
    public UpdateIntValueRequest(double value) {
        this(value, null);
    }
}
