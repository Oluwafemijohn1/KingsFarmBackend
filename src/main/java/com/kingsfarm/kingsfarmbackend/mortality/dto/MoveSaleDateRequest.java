package com.kingsfarm.kingsfarmbackend.mortality.dto;

import jakarta.validation.constraints.NotNull;

/** The day a mortality sale really belongs to. */
public record MoveSaleDateRequest(@NotNull java.time.LocalDate date) {
}
