package com.kingsfarm.kingsfarmbackend.feedmill.dto;

import com.kingsfarm.kingsfarmbackend.feedmill.FeedType;

public record FeedTypeResponse(Long id, String name) {
    public static FeedTypeResponse from(FeedType t) {
        return new FeedTypeResponse(t.getId(), t.getName());
    }
}
