package com.kingsfarm.kingsfarmbackend.security;

import com.kingsfarm.kingsfarmbackend.auth.AuthCookies;
import com.kingsfarm.kingsfarmbackend.user.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the access token primarily from the httpOnly {@code kf_access_token}
 * cookie (BACKEND_PLAN.md §11 decision #2 — the real frontend's only auth
 * path, since JS never touches that cookie's value). Falls back to
 * {@code Authorization: Bearer <token>} for non-browser API clients (Swagger
 * UI, curl, Postman, integration tests) that have no reason to hold a cookie
 * jar — this fallback never weakens the cookie's own protection, since it's
 * an entirely separate code path a browser-based XSS payload has no way to
 * reach. If it's a valid access token either way, sets the request's
 * authentication so Spring Security's {@code @PreAuthorize} checks (and
 * MustChangePasswordInterceptor) have something to work with. Runs after
 * {@link ApiKeyFilter} — an invalid API key never even gets this far.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null) {
            AuthenticatedPrincipal principal = jwtService.parse(token);
            if (principal != null) {
                // Primary role plus any Relief Access grants active at token-issuance time (see
                // AuthenticatedPrincipal/ReliefGrant's javadoc) — additive, never a replacement.
                List<GrantedAuthority> authorities = new ArrayList<>();
                authorities.add(new SimpleGrantedAuthority("ROLE_" + principal.role().name()));
                for (Role extra : principal.extraRoles()) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + extra.name()));
                }
                var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        chain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String cookieToken = AuthCookies.readCookie(request, AuthCookies.ACCESS_COOKIE);
        if (cookieToken != null) {
            return cookieToken;
        }
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring("Bearer ".length());
        }
        return null;
    }
}
