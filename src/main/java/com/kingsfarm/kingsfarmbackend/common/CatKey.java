package com.kingsfarm.kingsfarmbackend.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Mirrors shared.ts's CatKey/CAT_KEYS/CAT_LABELS/CRACK_WEIGHTS exactly — the
 * six egg categories shared across Production, Whole Egg, Crack Egg, and
 * Mortality. Do not add/rename/remove a value without updating the frontend
 * and BACKEND_PLAN.md at the same time.
 */
public enum CatKey {
    X_LARGE("xL", "X-Large", 1.0),
    LARGE("lg", "Large", 1.0),
    MEDIUM("md", "Medium", 3.0),
    SMALL("sm", "Small", 1.0),
    PULLET("pl", "Pullet", 0.5),
    WHITE("wh", "White", 1.0);

    private final String wire;
    private final String label;
    private final double crackWeight;

    CatKey(String wire, String label, double crackWeight) {
        this.wire = wire;
        this.label = label;
        this.crackWeight = crackWeight;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    /** Display label matching CAT_LABELS in shared.ts (e.g. "X-Large"). */
    public String label() {
        return label;
    }

    /** Pieces-per-cracked-egg-unit weight matching CRACK_WEIGHTS in shared.ts — used to convert Whole Egg's per-category Sales Crack into Production's single Crack Use total. */
    public double crackWeight() {
        return crackWeight;
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
