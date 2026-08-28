package com.kingsfarm.kingsfarmbackend.openingstock.dto;

import com.kingsfarm.kingsfarmbackend.openingstock.RequestStatus;
import jakarta.validation.constraints.NotNull;

/** decision must be APPROVED or DENIED — PENDING is rejected by the service (a request can't be "resolved" back to pending). */
public record ResolveOpeningStockRequestRequest(
        @NotNull RequestStatus decision
) {
}
