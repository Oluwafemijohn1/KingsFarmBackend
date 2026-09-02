package com.kingsfarm.kingsfarmbackend.wholeegg.dto;

import com.kingsfarm.kingsfarmbackend.common.CatKey;

/** Closing = Opening + Egg Production − Sales − Gift − Sales Crack (WholeEggView's Stock Overview formula, ported exactly). */
public record StockRowResponse(
        CatKey category,
        int opening,
        int production,
        int sales,
        int salesCrack,
        int gift,
        int closing,
        long price,
        boolean openingLocked
) {
}
