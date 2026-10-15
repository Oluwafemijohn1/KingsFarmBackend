package com.kingsfarm.kingsfarmbackend.wholeegg.dto;

import com.kingsfarm.kingsfarmbackend.common.CatKey;

/**
 * Closing = Opening + Egg Production − Sales − Gift − Sales Crack
 * (WholeEggView's Stock Overview formula, ported exactly). All quantity
 * fields are doubles — every one of them can carry a fractional crate
 * (1 crate = 30 eggs, 0.5 crate = 15 eggs) per the Crate Quantity &
 * Conversion spec; price stays a whole-Naira long.
 */
public record StockRowResponse(
        CatKey category,
        double opening,
        double production,
        double sales,
        double salesCrack,
        double gift,
        double closing,
        long price,
        boolean openingLocked
) {
}
