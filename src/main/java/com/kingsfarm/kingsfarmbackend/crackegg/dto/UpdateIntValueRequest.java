package com.kingsfarm.kingsfarmbackend.crackegg.dto;

import jakarta.validation.constraints.Min;

public record UpdateIntValueRequest(@Min(0) int value) {
}
