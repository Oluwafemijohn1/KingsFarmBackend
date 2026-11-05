package com.kingsfarm.kingsfarmbackend.backfill.dto;

/** open = the user may edit that day right now; pendingByMe = they already asked and are waiting. */
public record EditWindowResponse(boolean open, boolean grace, boolean pendingByMe) {
}
