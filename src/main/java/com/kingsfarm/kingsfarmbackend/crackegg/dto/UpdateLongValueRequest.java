package com.kingsfarm.kingsfarmbackend.crackegg.dto;

import jakarta.validation.constraints.Min;

public record UpdateLongValueRequest(@Min(0) long value) {
}
