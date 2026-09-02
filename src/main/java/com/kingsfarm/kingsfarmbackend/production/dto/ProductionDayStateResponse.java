package com.kingsfarm.kingsfarmbackend.production.dto;

import java.time.LocalDate;
import java.util.List;

public record ProductionDayStateResponse(
        LocalDate entryDate,
        List<CategoryStockRow> categories,
        int crackGoodOpen,
        int crackRoughOpen,
        int crackGoodProd,
        int crackRoughProd,
        double goodClassify,
        double roughClassify,
        int totalCrackFromWhole,
        double classifyTotal,
        boolean classifyMismatch,
        int crackGoodGiftAuto,
        int crackGoodSalesAuto,
        double crackGoodClosing,
        int crackRoughClosing,
        String enteredBy,
        String updatedBy,
        boolean editable
) {
}
