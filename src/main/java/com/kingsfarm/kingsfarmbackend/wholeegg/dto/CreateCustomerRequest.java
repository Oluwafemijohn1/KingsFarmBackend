package com.kingsfarm.kingsfarmbackend.wholeegg.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Customer creation and its optional Opening Balance are one atomic request
 * — matches NewCustomerForm's single "Save Customer" action, which creates
 * the customer then immediately seeds an "opening" transaction if an amount
 * was entered. At most one of openingCredit/openingAdvance may be nonzero —
 * a customer can't simultaneously owe money and hold an advance.
 */
public record CreateCustomerRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank String phone,
        @NotBlank String state,
        String lga,
        String street,
        @Min(0) long openingCredit,
        @Min(0) long openingAdvance
) {
}
