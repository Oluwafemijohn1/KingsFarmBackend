package com.kingsfarm.kingsfarmbackend.security;

import com.kingsfarm.kingsfarmbackend.user.Role;

/**
 * What ends up as {@code SecurityContextHolder}'s principal for every
 * authenticated request. Built directly from the access token's claims (see
 * JwtService/JwtAuthenticationFilter) rather than a database lookup on every
 * request — keeps authenticated requests fast, at the cost of a deactivated
 * account staying valid until its current access token expires (at most
 * app.security.jwt.access-token-minutes). Tighter, immediate revocation
 * happens at the refresh-token layer instead (see AuthService.refresh).
 */
public record AuthenticatedPrincipal(Long userId, String username, Role role, boolean mustChangePassword) {
}
