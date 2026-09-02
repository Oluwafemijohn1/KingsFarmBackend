package com.kingsfarm.kingsfarmbackend.birdstock.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreatePenRequest(
        @NotBlank String name,
        @Min(0) int opening
) {
}
