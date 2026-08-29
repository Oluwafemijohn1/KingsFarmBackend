package com.kingsfarm.kingsfarmbackend.mortality.dto;

import jakarta.validation.constraints.Min;

public record UpdateGiftLogEntryRequest(
        @Min(0) int good,
        @Min(0) int dry,
        @Min(0) int runt,
        String recipient,
        String authorizer
) {
}
