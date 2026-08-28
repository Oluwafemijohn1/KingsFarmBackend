package com.kingsfarm.kingsfarmbackend.openingstock.dto;

import com.kingsfarm.kingsfarmbackend.common.Mod;
import com.kingsfarm.kingsfarmbackend.openingstock.OpeningStockRequest;
import com.kingsfarm.kingsfarmbackend.openingstock.RequestStatus;

import java.time.Instant;

public record OpeningStockRequestResponse(
        Long id,
        Mod module,
        String scope,
        String scopeLabel,
        String requestedByUsername,
        String requestedByName,
        String reason,
        Instant requestedAt,
        RequestStatus status,
        Instant resolvedAt,
        String resolvedByUsername
) {
    public static OpeningStockRequestResponse from(OpeningStockRequest r) {
        return new OpeningStockRequestResponse(
                r.getId(), r.getModule(), r.getScope(), r.getScopeLabel(),
                r.getRequestedBy().getUsername(), r.getRequestedBy().getFullName(),
                r.getReason(), r.getRequestedAt(), r.getStatus(), r.getResolvedAt(),
                r.getResolvedBy() != null ? r.getResolvedBy().getUsername() : null
        );
    }
}
