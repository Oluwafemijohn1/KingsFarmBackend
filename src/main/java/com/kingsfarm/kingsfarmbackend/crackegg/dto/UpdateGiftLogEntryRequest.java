package com.kingsfarm.kingsfarmbackend.crackegg.dto;

import jakarta.validation.constraints.Min;

public record UpdateGiftLogEntryRequest(
        @Min(0) int qty,
        String recipient,
        String authorizer
) {
}
