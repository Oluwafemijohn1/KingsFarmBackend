package com.kingsfarm.kingsfarmbackend.mortality.dto;

import jakarta.validation.constraints.Min;

/** date: the finished working day the opening is for; null = today. */
public record UpdateOpeningRequest(@Min(0) int value, java.time.LocalDate date) {
}
