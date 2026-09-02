package com.kingsfarm.kingsfarmbackend.production.dto;

import com.kingsfarm.kingsfarmbackend.common.CatKey;

/**
 * Closing = Opening + Production − Crack Use − Total Sales − Gift (last
 * three auto-received from Whole Egg). opening/production/closing are
 * double, not int — Production by Pen accepts partial crates (e.g. 1.5), so
 * that precision has to survive the roll-up here and the day-to-day carry-
 * forward in ProductionDayState. crackUse/totalSales/gift stay int — those
 * come from Whole Egg, which is out of this change's scope and still counts
 * in whole crates.
 */
public record CategoryStockRow(
        CatKey category,
        double opening,
        double production,
        int crackUse,
        int totalSales,
        int gift,
        double closing,
        boolean openingLocked
) {
}
