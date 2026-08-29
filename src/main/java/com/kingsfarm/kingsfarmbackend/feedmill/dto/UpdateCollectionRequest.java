package com.kingsfarm.kingsfarmbackend.feedmill.dto;

import com.kingsfarm.kingsfarmbackend.feedmill.FeedCollector;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Every field is editable in place, matching the frontend's CollectionDraft ({ by, s, g, f }). */
public record UpdateCollectionRequest(
        @NotNull FeedCollector collectedBy,
        @PositiveOrZero double fishStarterKg,
        @PositiveOrZero double fishGrowerKg,
        @PositiveOrZero double fishFinisherKg
) {
}
