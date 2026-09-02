package com.kingsfarm.kingsfarmbackend.production.dto;

import jakarta.validation.constraints.Min;

/** All optional — null means "leave unchanged", same partial-update convention as Bird Stock. */
public record UpdateCrackFieldsRequest(
        @Min(0) Integer crackGoodOpen,
        @Min(0) Integer crackRoughOpen,
        @Min(0) Integer crackGoodProd,
        @Min(0) Integer crackRoughProd,
        @Min(0) Double goodClassify,
        @Min(0) Double roughClassify
) {
}
