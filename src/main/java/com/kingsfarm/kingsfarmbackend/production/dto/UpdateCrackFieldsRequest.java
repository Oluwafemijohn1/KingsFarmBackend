package com.kingsfarm.kingsfarmbackend.production.dto;

import jakarta.validation.constraints.DecimalMin;

/**
 * All optional — null means "leave unchanged", same partial-update
 * convention as Bird Stock. Double, not Integer/@Min — these are
 * crate-based quantities and can be fractional. Production Manager's own
 * fields only — goodClassify/roughClassify moved to
 * {@link UpdateClassifyFieldsRequest}, since classifying Crack Use is the
 * Crack Egg Manager's call, not Production's.
 */
public record UpdateCrackFieldsRequest(
        @DecimalMin("0") Double crackGoodOpen,
        @DecimalMin("0") Double crackRoughOpen,
        @DecimalMin("0") Double crackGoodProd,
        @DecimalMin("0") Double crackRoughProd
) {
}
