package com.kingsfarm.kingsfarmbackend.backfill.dto;

import com.kingsfarm.kingsfarmbackend.common.Mod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateBackfillRequestRequest(@NotNull Mod module, @NotNull LocalDate forDate, @NotBlank String reason) {
}
