package com.kingsfarm.kingsfarmbackend.user;

/**
 * Mirrors the frontend's {@code Role} union in {@code shared.ts} exactly —
 * do not add/rename/remove a value here without updating the frontend and
 * BACKEND_PLAN.md §4 at the same time.
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
    public String label() {
        return label;
    }

    /** Human-readable label rather than the Java constant name — reads better in audit-log detail strings built via SpEL string concatenation. */
    @Override
    public String toString() {
        return label;
    }
}
