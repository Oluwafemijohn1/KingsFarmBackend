package com.kingsfarm.kingsfarmbackend.mortality;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The five dead-bird categories on MortalityView — unrelated to {@code CatKey}
 * (egg sizes); do not confuse the two. Only GOOD/DRY/RUNT are saleable/
 * giftable (see {@link #saleable()}); GREEN routes to Catfish Feed only and
 * PM_REJECT routes to Disposal only, per the business-rule cards on the
 * Stock Overview tab.
 */
public enum MortCat {
    GOOD("Good", true),
    DRY("Dry", true),
    RUNT("Runt", true),
    GREEN("Green", false),
    PM_REJECT("PM/Reject", false);

    private final String wire;
    private final boolean saleable;

    MortCat(String wire, boolean saleable) {
        this.wire = wire;
        this.saleable = saleable;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    /** True for Good/Dry/Runt — the only categories Sales & Gift apply to. */
    public boolean saleable() {
        return saleable;
    }

    @JsonCreator
    public static MortCat fromWire(String value) {
        for (MortCat c : values()) {
            if (c.wire.equals(value)) {
                return c;
            }
        }
        throw new IllegalArgumentException("Unknown mortality category: " + value);
    }

    @Override
    public String toString() {
        return wire;
    }
}
