package com.kingsfarm.kingsfarmbackend.mortality.dto;

import com.kingsfarm.kingsfarmbackend.mortality.MortCat;

/**
 * One row of the Stock Overview table. Closing formula per category (ported
 * exactly from MortalityView.tsx's {@code closing()}):
 * <ul>
 *   <li>Good/Dry/Runt: opening + produced − sales − gift</li>
 *   <li>Green: opening + produced − catfish</li>
 *   <li>PM/Reject: opening + produced − disposal</li>
 * </ul>
 */
public record MortCategoryStockRow(
        MortCat category,
        int opening,
        boolean openingLocked,
        int produced,
        int sales,
        int gift,
        int catfish,
        int disposal,
        int closing
) {
}
