package com.kingsfarm.kingsfarmbackend.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Mirrors shared.ts's PaymentMethod type ("Cash" | "Transfer") exactly — shared across Whole Egg, Crack Egg, and Mortality sales. */
public enum PaymentMethod {
    CASH("Cash"),
    TRANSFER("Transfer");

    private final String wire;

    PaymentMethod(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    @JsonCreator
    public static PaymentMethod fromWire(String value) {
        for (PaymentMethod m : values()) {
            if (m.wire.equalsIgnoreCase(value)) {
                return m;
            }
        }
        throw new IllegalArgumentException("Unknown payment method: " + value);
    }

    @Override
    public String toString() {
        return wire;
    }
}
