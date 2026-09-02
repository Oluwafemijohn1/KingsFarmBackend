package com.kingsfarm.kingsfarmbackend.mortality.dto;

import jakarta.validation.constraints.Min;

public record UpdateOpeningRequest(@Min(0) int value) {
}
