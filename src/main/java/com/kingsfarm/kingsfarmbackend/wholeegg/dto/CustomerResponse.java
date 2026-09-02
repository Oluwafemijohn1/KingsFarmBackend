package com.kingsfarm.kingsfarmbackend.wholeegg.dto;

import com.kingsfarm.kingsfarmbackend.wholeegg.Customer;

import java.time.Instant;

/**
 * visitCount/totalCrates/totalRevenue/lastPurchaseAt back the Customers
 * directory table (WholeEggView's customerStats) — added alongside the
 * Phase 5 frontend swap since the directory needs them per-row and there was
 * previously no server-side way to compute them (see
 * WholeEggService#toCustomerResponse). All four respect the same "manager
 * sees this year only, Administrator sees everything" restriction as
 * customerHistory/allTransactions.
 */
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
        long balance,
        long visitCount,
        long totalCrates,
        long totalRevenue,
        Instant lastPurchaseAt
) {
    public static CustomerResponse from(Customer c, long balance, long visitCount, long totalCrates, long totalRevenue, Instant lastPurchaseAt) {
        return new CustomerResponse(c.getId(), c.getFirstName(), c.getLastName(), c.getPhone(),
                c.getState(), c.getLga(), c.getStreet(), c.getCreatedAt(), c.getCreatedBy(), balance,
                visitCount, totalCrates, totalRevenue, lastPurchaseAt);
    }
}
