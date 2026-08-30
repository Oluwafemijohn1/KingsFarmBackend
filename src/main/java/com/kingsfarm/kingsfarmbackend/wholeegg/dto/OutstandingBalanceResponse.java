package com.kingsfarm.kingsfarmbackend.wholeegg.dto;

/** Farm-wide totals — see WeSaleTransactionRepository#outstandingTotals for how these are computed. */
public record OutstandingBalanceResponse(long totalCredit, long totalAdvance) {
}
