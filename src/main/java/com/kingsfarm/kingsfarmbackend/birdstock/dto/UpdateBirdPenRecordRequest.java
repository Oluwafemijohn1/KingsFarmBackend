package com.kingsfarm.kingsfarmbackend.birdstock.dto;

import jakarta.validation.constraints.Min;

/** Partial update — every field is optional (null = "leave unchanged"), matching the frontend's per-cell onChange pattern of writing one field at a time. */
public record UpdateBirdPenRecordRequest(
        @Min(0) Integer opening,
        @Min(0) Integer mortality,
        @Min(0) Integer birdSales,
        @Min(0) Integer restocking,
        String remarks
) {
}
