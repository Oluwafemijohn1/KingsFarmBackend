package com.kingsfarm.kingsfarmbackend.security;

import com.kingsfarm.kingsfarmbackend.user.Role;

import java.util.List;

/**
 * What ends up as {@code SecurityContextHolder}'s principal for every
 * authenticated request. Built directly from the access token's claims (see
 * JwtService/JwtAuthenticationFilter) rather than a database lookup on every
 * request — keeps authenticated requests fast, at the cost of a deactivated
 * account staying valid until its current access token expires (at most
 * app.security.jwt.access-token-minutes). Tighter, immediate revocation
 * happens at the refresh-token layer instead (see AuthService.refresh).
 * <p>
 * {@code extraRoles} carries any Relief Access grants active for this user
 * as the relieving officer at the moment the token was issued (see
 * ReliefGrant's javadoc) — same staleness tradeoff as everything else here:
 * a grant revoked mid-session doesn't retract access until the next
 * login/refresh, not instantly.
 */
public record AuthenticatedPrincipal(Long userId, String username, Role role, boolean mustChangePassword, List<Role> extraRoles) {
}
