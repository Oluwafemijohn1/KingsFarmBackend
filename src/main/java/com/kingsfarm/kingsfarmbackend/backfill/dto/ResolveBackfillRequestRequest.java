package com.kingsfarm.kingsfarmbackend.backfill.dto;

import com.kingsfarm.kingsfarmbackend.openingstock.RequestStatus;
import jakarta.validation.constraints.NotNull;

public record ResolveBackfillRequestRequest(@NotNull RequestStatus decision) {
}
