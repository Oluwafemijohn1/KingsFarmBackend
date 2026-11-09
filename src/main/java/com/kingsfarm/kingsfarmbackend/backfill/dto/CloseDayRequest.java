package com.kingsfarm.kingsfarmbackend.backfill.dto;

import com.kingsfarm.kingsfarmbackend.common.Mod;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** {@code confirmNothingElse}: the manager confirms nothing more happened on a past day that still shows missing entries. */
public record CloseDayRequest(@NotNull Mod module, @NotNull LocalDate date, Boolean confirmNothingElse) {
}
