package com.kingsfarm.kingsfarmbackend.wholeegg.dto;

/** Sold = value of the goods sold; cash / transfer = money received; received = cash + transfer. Shared by every module's sales page. */
public record SalesSummaryResponse(double sold, long cash, long transfer, long count) {
    public long received() {
        return cash + transfer;
    }
}
