package com.kingsfarm.kingsfarmbackend.crackegg.dto;

import jakarta.validation.constraints.DecimalMin;

/** double, not int/@Min — backs Good/Rough Crack Opening Stock, Gift Qty, and Feed Mill usage, all of which can be fractional crates. */
public record UpdateIntValueRequest(@DecimalMin("0") double value) {
}
