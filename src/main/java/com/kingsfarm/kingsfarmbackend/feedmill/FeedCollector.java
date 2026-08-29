package com.kingsfarm.kingsfarmbackend.feedmill;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Who physically collected a fish feed batch — a location label ("Farm" or "Prince"), not a user account. Mirrors FeedMillView.tsx's CollectionLogEntry.by. */
public enum FeedCollector {
    FARM("Farm"),
    PRINCE("Prince");

    private final String wire;

    FeedCollector(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    @JsonCreator
    public static FeedCollector fromWire(String value) {
        for (FeedCollector c : values()) {
            if (c.wire.equalsIgnoreCase(value)) {
                return c;
            }
        }
        throw new IllegalArgumentException("Unknown collector: " + value);
    }

    @Override
    public String toString() {
        return wire;
    }
}
