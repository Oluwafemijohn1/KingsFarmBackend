package com.kingsfarm.kingsfarmbackend.crackegg.dto;

/**
 * Good Crack Closing = Opening + Production + Received − Gift − Sales.
 * Rough Crack Closing = Opening + Production + Received − Feed Mill Usage.
 * "Received" (goodReceived/roughReceived) is Production's classified figure
 * (goodClassify/roughClassify), read live from ProductionService.
 */
public record StockSummaryResponse(
        int gcOpening, boolean gcOpeningLocked, int gcProduced, double gcReceived,
        long gcSellingPrice, int gcGiftQty, String gcGiftRecipient, String gcGiftAuthorizer,
        int gcTotalSales, long gcRevenue, int gcClosing,
        int rcOpening, boolean rcOpeningLocked, int rcProduced, double rcReceived,
        int rcFeedMill, int rcClosing
) {
}
