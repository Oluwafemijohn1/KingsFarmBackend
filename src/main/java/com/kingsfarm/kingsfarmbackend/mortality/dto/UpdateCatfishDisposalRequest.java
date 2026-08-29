package com.kingsfarm.kingsfarmbackend.mortality.dto;

import jakarta.validation.constraints.Min;

public record UpdateCatfishDisposalRequest(
        @Min(0) int catfishQty,
        @Min(0) int disposalQty
) {
}
