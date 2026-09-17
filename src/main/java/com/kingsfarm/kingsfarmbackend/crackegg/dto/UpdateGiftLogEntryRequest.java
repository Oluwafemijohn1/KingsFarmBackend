package com.kingsfarm.kingsfarmbackend.crackegg.dto;

import jakarta.validation.constraints.DecimalMin;

public record UpdateGiftLogEntryRequest(
        @DecimalMin("0") double qty,
        String recipient,
        String authorizer
) {
}
