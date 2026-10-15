package com.kingsfarm.kingsfarmbackend.production.dto;

import jakarta.validation.constraints.DecimalMin;

/**
 * Split out of {@link UpdateCrackFieldsRequest} — classifying the day's
 * received Crack Use into Good/Rough is the Crack Egg Manager's call, not
 * Production's (they only own crackGoodOpen/crackRoughOpen/crackGoodProd/
 * crackRoughProd, still covered by that DTO). Both fields optional — null
 * means "leave unchanged," same partial-update convention used everywhere
 * else. Double, not Integer/@Min — these are crate-based quantities and can
 * be fractional.
 */
public record UpdateClassifyFieldsRequest(
        @DecimalMin("0") Double goodClassify,
        @DecimalMin("0") Double roughClassify
) {
}
