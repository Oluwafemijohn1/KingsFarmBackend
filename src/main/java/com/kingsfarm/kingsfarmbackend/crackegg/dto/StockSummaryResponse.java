package com.kingsfarm.kingsfarmbackend.crackegg.dto;

/**
 * Good Crack Closing = Opening + Production + Received − Gift − Sales.
 * Rough Crack Closing = Opening + Production + Received − Feed Mill Usage.
 * "Received" (goodReceived/roughReceived) is Production's classified figure
 * (goodClassify/roughClassify), read live from ProductionService.
 */
public record StockSummaryResponse(
        double gcOpening, boolean gcOpeningLocked, double gcProduced, double gcReceived,
        long gcSellingPrice, double gcGiftQty, String gcGiftRecipient, String gcGiftAuthorizer,
        double gcTotalSales, double gcRevenue, double gcClosing,
        double rcOpening, boolean rcOpeningLocked, double rcProduced, double rcReceived,
        double rcFeedMill, double rcClosing
) {
}
