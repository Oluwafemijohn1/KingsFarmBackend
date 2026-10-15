package com.kingsfarm.kingsfarmbackend.production.dto;

import java.time.LocalDate;
import java.util.List;

public record ProductionDayStateResponse(
        LocalDate entryDate,
        List<CategoryStockRow> categories,
        double crackGoodOpen,
        double crackRoughOpen,
        double crackGoodProd,
        double crackRoughProd,
        double goodClassify,
        double roughClassify,
        double totalCrackFromWhole,
        double classifyTotal,
        boolean classifyMismatch,
        double crackGoodGiftAuto,
        double crackGoodSalesAuto,
        double crackGoodClosing,
        double crackRoughClosing,
        String enteredBy,
        String updatedBy,
        boolean editable
) {
}
