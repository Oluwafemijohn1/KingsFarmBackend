package com.kingsfarm.kingsfarmbackend.mortality.dto;

import com.kingsfarm.kingsfarmbackend.mortality.MortPenEntry;

import java.time.LocalDate;

public record MortPenEntryResponse(
        Long id,
        Long penId,
        String penName,
        LocalDate entryDate,
        int good,
        int dry,
        int runt,
        int green,
        int pmReject,
        int total,
        String enteredBy,
        String updatedBy,
        boolean editable
) {
    public static MortPenEntryResponse from(MortPenEntry e, boolean editable) {
        return new MortPenEntryResponse(
                e.getId(), e.getPen().getId(), e.getPen().getName(), e.getEntryDate(),
                e.getGood(), e.getDry(), e.getRunt(), e.getGreen(), e.getPmReject(), e.total(),
                e.getEnteredBy(), e.getUpdatedBy(), editable
        );
    }
}
