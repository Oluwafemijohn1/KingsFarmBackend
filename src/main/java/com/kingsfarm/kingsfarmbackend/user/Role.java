package com.kingsfarm.kingsfarmbackend.user;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Mirrors the frontend's {@code Role} union in {@code shared.ts} exactly —
 * do not add/rename/remove a value here without updating the frontend and
 * BACKEND_PLAN.md §4 at the same time. Serializes to/from the label string
 * (e.g. "Production Manager"), not the Java enum constant name — added
 * during Phase 5 to match {@code Mod}'s existing wire-format precedent, so
 * the frontend can consume {@code role}/{@code extraRoles} directly as its
 * own {@code Role} string union with no translation layer. Several DTOs
 * built before this (e.g. `CreateUserResponse`) still carry a separate,
 * now-redundant `roleLabel` field alongside `role` — harmless duplication
 * now that both serialize to the same string, left as-is rather than
 * touched as a drive-by refactor; worth a cleanup pass in Phase 6.
 */
public enum Role {
    ADMINISTRATOR("Administrator"),
    PRODUCTION_MANAGER("Production Manager"),
    WHOLE_EGG_MANAGER("Whole Egg Manager"),
    CRACK_EGG_MANAGER("Crack Egg Manager"),
    FEED_MILL_MANAGER("Feed Mill Manager"),
    MORTALITY_MANAGER("Mortality Manager"),
    MANAGING_DIRECTOR("Managing Director");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    /** The exact string the frontend expects for this role (see shared.ts's Role type). */
    @JsonValue
    public String label() {
        return label;
    }

    @JsonCreator
    public static Role fromLabel(String value) {
        for (Role r : values()) {
            if (r.label.equals(value)) {
                return r;
            }
        }
        throw new IllegalArgumentException("Unknown role: " + value);
    }

    /** Human-readable label rather than the Java constant name — reads better in audit-log detail strings built via SpEL string concatenation. */
    @Override
    public String toString() {
        return label;
    }
}
