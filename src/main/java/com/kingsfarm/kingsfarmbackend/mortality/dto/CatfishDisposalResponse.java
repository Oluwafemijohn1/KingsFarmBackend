package com.kingsfarm.kingsfarmbackend.mortality.dto;

import com.kingsfarm.kingsfarmbackend.mortality.MortCatfishDisposalState;

import java.time.LocalDate;

public record CatfishDisposalResponse(
        LocalDate entryDate,
        int catfishQty,
        int disposalQty,
        int greenAvailable,
        int pmRejectAvailable,
        String enteredBy,
        String updatedBy,
        boolean editable
) {
    public static CatfishDisposalResponse from(MortCatfishDisposalState s, int greenAvailable, int pmRejectAvailable, boolean editable) {
        return new CatfishDisposalResponse(
                s.getEntryDate(), s.getCatfishQty(), s.getDisposalQty(), greenAvailable, pmRejectAvailable,
                s.getEnteredBy(), s.getUpdatedBy(), editable
        );
    }
}
