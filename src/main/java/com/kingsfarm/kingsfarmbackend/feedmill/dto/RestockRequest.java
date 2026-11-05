package com.kingsfarm.kingsfarmbackend.feedmill.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** qty is the amount received on that day; a negative number corrects an earlier over-entry. */
public record RestockRequest(@NotNull LocalDate date, double qty) {
}
