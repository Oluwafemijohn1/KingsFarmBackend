package com.kingsfarm.kingsfarmbackend.birdstock.dto;

import com.kingsfarm.kingsfarmbackend.birdstock.BirdPenRecord;

import java.time.LocalDate;

public record BirdPenRecordResponse(
        Long id,
        Long penId,
        String penName,
        LocalDate entryDate,
        int opening,
        int mortality,
        int birdSales,
        int restocking,
        int closing,
        String remarks,
        String enteredBy,
        String updatedBy,
        boolean editable,
        boolean openingLocked
) {
    public static BirdPenRecordResponse from(BirdPenRecord record, boolean editable, boolean openingLocked) {
        return new BirdPenRecordResponse(
                record.getId(), record.getPen().getId(), record.getPen().getName(), record.getEntryDate(),
                record.getOpening(), record.getMortality(), record.getBirdSales(), record.getRestocking(),
                record.closing(), record.getRemarks(), record.getEnteredBy(), record.getUpdatedBy(),
                editable, openingLocked
        );
    }
}
