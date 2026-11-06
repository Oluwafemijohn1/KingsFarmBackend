package com.kingsfarm.kingsfarmbackend.backfill.dto;

import com.kingsfarm.kingsfarmbackend.common.Mod;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CloseDayRequest(@NotNull Mod module, @NotNull LocalDate date) {
}
