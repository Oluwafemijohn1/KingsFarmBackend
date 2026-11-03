package com.kingsfarm.kingsfarmbackend.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Mirrors shared.ts's CatKey/CAT_KEYS/CAT_LABELS exactly — the
 * six egg categories shared across Production, Whole Egg, Crack Egg, and
 * Mortality. Do not add/rename/remove a value without updating the frontend
 * and BACKEND_PLAN.md at the same time.
 */
public enum CatKey {
    X_LARGE("xL", "X-Large"),
    LARGE("lg", "Large"),
    MEDIUM("md", "Medium"),
    SMALL("sm", "Small"),
    PULLET("pl", "Pullet"),
    WHITE("wh", "White");

    private final String wire;
    private final String label;

    CatKey(String wire, String label) {
        this.wire = wire;
        this.label = label;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    /** Display label matching CAT_LABELS in shared.ts (e.g. "X-Large"). */
    public String label() {
        return label;
    }

    @JsonCreator
    public static CatKey fromWire(String value) {
        for (CatKey k : values()) {
            if (k.wire.equals(value)) {
                return k;
            }
        }
        throw new IllegalArgumentException("Unknown category: " + value);
    }

    /** Wire form (e.g. "xL") rather than the Java constant name — reads better in audit-log detail strings and matches the frontend's own key. */
    @Override
    public String toString() {
        return wire;
    }
}
