package com.kingsfarm.kingsfarmbackend.production.dto;

import com.kingsfarm.kingsfarmbackend.common.CatKey;

/**
 * Closing = Opening + Production − Crack Use − Total Sales − Gift (last
 * three auto-received from Whole Egg). Every quantity field here is double —
 * Production by Pen accepts partial crates (e.g. 1.5), and Whole Egg's own
 * crackUse/totalSales/gift are fractional-capable too (Crate Quantity &
 * Conversion spec), so that precision has to survive the roll-up here and
 * the day-to-day carry-forward in ProductionDayState.
 */
public record CategoryStockRow(
        CatKey category,
        double opening,
        double production,
        double crackUse,
        double totalSales,
        double gift,
        double closing,
        boolean openingLocked
) {
}
