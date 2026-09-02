package com.kingsfarm.kingsfarmbackend.production.dto;

import com.kingsfarm.kingsfarmbackend.common.CatKey;

/** Closing = Opening + Production − Crack Use − Total Sales − Gift (last three auto-received from Whole Egg, stubbed at zero until that module exists — see ProductionService). */
public record CategoryStockRow(
        CatKey category,
        int opening,
        int production,
        int crackUse,
        int totalSales,
        int gift,
        int closing,
        boolean openingLocked
) {
}
