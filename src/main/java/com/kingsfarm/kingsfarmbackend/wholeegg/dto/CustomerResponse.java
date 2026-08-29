package com.kingsfarm.kingsfarmbackend.wholeegg.dto;

import com.kingsfarm.kingsfarmbackend.wholeegg.Customer;

import java.time.Instant;

public record CustomerResponse(
        Long id,
        String firstName,
        String lastName,
        String phone,
        String state,
        String lga,
        String street,
        Instant createdAt,
        String createdBy,
        /** Positive = customer owes this much; negative = customer has this much in advance; zero = settled. */
        long balance
) {
    public static CustomerResponse from(Customer c, long balance) {
        return new CustomerResponse(c.getId(), c.getFirstName(), c.getLastName(), c.getPhone(),
                c.getState(), c.getLga(), c.getStreet(), c.getCreatedAt(), c.getCreatedBy(), balance);
    }
}
