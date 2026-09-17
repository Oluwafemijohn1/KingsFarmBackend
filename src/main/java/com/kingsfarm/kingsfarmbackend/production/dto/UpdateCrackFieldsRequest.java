package com.kingsfarm.kingsfarmbackend.production.dto;

import jakarta.validation.constraints.DecimalMin;

/** All optional — null means "leave unchanged", same partial-update convention as Bird Stock. Double, not Integer/@Min — these are crate-based quantities and can be fractional. */
public record UpdateCrackFieldsRequest(
        @DecimalMin("0") Double crackGoodOpen,
        @DecimalMin("0") Double crackRoughOpen,
        @DecimalMin("0") Double crackGoodProd,
        @DecimalMin("0") Double crackRoughProd,
        @DecimalMin("0") Double goodClassify,
        @DecimalMin("0") Double roughClassify
) {
}
