package com.kingsfarm.kingsfarmbackend.wholeegg.dto;

import jakarta.validation.constraints.Min;

/** Sets (or clears) a customer's opening balance after the profile already exists. At most one of the two may be nonzero; both zero removes it. */
public record SetOpeningBalanceRequest(
        @Min(0) long openingCredit,
        @Min(0) long openingAdvance
) {
}
