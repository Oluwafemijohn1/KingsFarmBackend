package com.kingsfarm.kingsfarmbackend.openingstock.dto;

/**
 * pendingRequestByMe exists because {@code GET /opening-stock/requests} is
 * Administrator-only (see OpeningStockController) — a manager has no other
 * way to find out whether their own unlock request for this field is still
 * pending, including after a page reload. Scoped to "by me" rather than "any
 * pending request" so two managers who somehow share a scope don't see each
 * other's in-flight requests.
 */
public record LockStatusResponse(boolean locked, boolean pendingRequestByMe) {
}
