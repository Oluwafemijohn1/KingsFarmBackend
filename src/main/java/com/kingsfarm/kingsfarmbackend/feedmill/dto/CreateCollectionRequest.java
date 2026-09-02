package com.kingsfarm.kingsfarmbackend.feedmill.dto;

import com.kingsfarm.kingsfarmbackend.feedmill.FeedCollector;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record CreateCollectionRequest(
        @NotNull FeedCollector collectedBy,
        @PositiveOrZero double fishStarterKg,
        @PositiveOrZero double fishGrowerKg,
        @PositiveOrZero double fishFinisherKg
) {
}
