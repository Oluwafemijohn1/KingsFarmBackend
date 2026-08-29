package com.kingsfarm.kingsfarmbackend.feedmill.dto;

import jakarta.validation.constraints.PositiveOrZero;

/** Generic single-quantity PATCH body reused across ingredient opening/added/min and fish feed opening. */
public record UpdateQuantityRequest(@PositiveOrZero double value) {
}
