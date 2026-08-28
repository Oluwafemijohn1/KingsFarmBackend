package com.kingsfarm.kingsfarmbackend.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Mirrors the frontend's {@code Mod} union in {@code shared.ts} exactly —
 * do not add/rename/remove a value here without updating the frontend and
 * BACKEND_PLAN.md at the same time. Serializes to/from the same hyphenated
 * wire strings the frontend already uses (e.g. "whole-egg"), not the Java
 * enum constant name, so the JSON contract matches without any frontend
 * translation layer.
 */
public enum Mod {
    DASHBOARD("dashboard"),
    BIRD_STOCK("bird-stock"),
    PRODUCTION("production"),
    WHOLE_EGG("whole-egg"),
    CRACK_EGG("crack-egg"),
    FEED_MILL("feed-mill"),
    MORTALITY("mortality"),
    ADMIN("admin"),
    GENERAL_REPORT("general-report"),
    PROFILE("profile");

    private final String wire;

    Mod(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    @JsonCreator
    public static Mod fromWire(String value) {
        for (Mod m : values()) {
            if (m.wire.equals(value)) {
                return m;
            }
        }
        throw new IllegalArgumentException("Unknown module: " + value);
    }

    /** Wire form (e.g. "whole-egg") rather than the Java constant name — reads better in audit-log detail strings built via SpEL string concatenation. */
    @Override
    public String toString() {
        return wire;
    }
}
