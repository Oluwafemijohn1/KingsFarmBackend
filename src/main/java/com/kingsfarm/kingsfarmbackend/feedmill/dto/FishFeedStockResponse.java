package com.kingsfarm.kingsfarmbackend.feedmill.dto;

import com.kingsfarm.kingsfarmbackend.feedmill.FishFeedStock;

public record FishFeedStockResponse(
        String type,
        double opening,
        boolean openingLocked,
        double added,
        double collected,
        double closing
) {
    public static FishFeedStockResponse from(FishFeedStock s, boolean openingLocked) {
        return new FishFeedStockResponse(s.getType(), s.getOpening(), openingLocked, s.getAdded(), s.getCollected(), s.closing());
    }
}
