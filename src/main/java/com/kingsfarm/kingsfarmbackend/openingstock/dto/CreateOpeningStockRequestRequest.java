package com.kingsfarm.kingsfarmbackend.openingstock.dto;

import com.kingsfarm.kingsfarmbackend.common.Mod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateOpeningStockRequestRequest(
        @NotNull Mod module,
        @NotBlank String scope,
        @NotBlank String scopeLabel,
        @NotBlank String reason
) {
}
