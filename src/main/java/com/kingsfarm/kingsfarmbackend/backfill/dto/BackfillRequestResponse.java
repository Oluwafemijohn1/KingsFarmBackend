package com.kingsfarm.kingsfarmbackend.backfill.dto;

import com.kingsfarm.kingsfarmbackend.backfill.BackfillRequest;
import com.kingsfarm.kingsfarmbackend.common.Mod;
import com.kingsfarm.kingsfarmbackend.openingstock.RequestStatus;

import java.time.Instant;
import java.time.LocalDate;

public record BackfillRequestResponse(
        Long id, Mod module, LocalDate forDate, String requestedByUsername, String requestedByName,
        String reason, Instant requestedAt, RequestStatus status, Instant resolvedAt,
        String resolvedByUsername, Instant expiresAt
) {
    public static BackfillRequestResponse from(BackfillRequest r) {
        return new BackfillRequestResponse(
                r.getId(), r.getModule(), r.getForDate(),
                r.getRequestedBy().getUsername(), r.getRequestedBy().getFullName(),
                r.getReason(), r.getRequestedAt(), r.getStatus(), r.getResolvedAt(),
                r.getResolvedBy() != null ? r.getResolvedBy().getUsername() : null, r.getExpiresAt());
    }
}
