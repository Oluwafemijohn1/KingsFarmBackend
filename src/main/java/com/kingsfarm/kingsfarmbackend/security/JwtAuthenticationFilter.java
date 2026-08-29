package com.kingsfarm.kingsfarmbackend.security;

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
 * Reads {@code Authorization: Bearer <token>}, and if it's a valid access
 * token, sets the request's authentication so Spring Security's
 * {@code @PreAuthorize} checks (and MustChangePasswordInterceptor) have
 * something to work with. Runs after {@link ApiKeyFilter} — an invalid API
 * key never even gets this far.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring("Bearer ".length());
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
}
