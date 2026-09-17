package com.kingsfarm.kingsfarmbackend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kingsfarm.kingsfarmbackend.common.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.List;

/**
 * Wires the three custom filters in front of everything else, in order:
 * RateLimitFilter (Phase 6 — rejects a flood before anything else runs),
 * then ApiKeyFilter (shared-secret gate, rejects any client without
 * X-API-Key), then JwtAuthenticationFilter (per-user auth) populates the
 * SecurityContext for @PreAuthorize checks on individual controller methods.
 * Fine-grained role rules live on the controllers themselves (matching the
 * ACCESS map in the frontend's shared.ts) rather than here, since most of
 * them are module-specific; this class only decides what's public vs. must
 * be authenticated at all.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final AppSecurityProperties securityProperties;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public SecurityConfig(AppSecurityProperties securityProperties, JwtService jwtService, ObjectMapper objectMapper) {
        this.securityProperties = securityProperties;
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Deliberately still disabled now that auth moved to httpOnly cookies
                // (BACKEND_PLAN.md §11 decision #2) — CSRF protection here comes from
                // both cookies being SameSite=Strict (see AuthCookies' javadoc) instead
                // of Spring Security's token-based CSRF machinery, which would need the
                // frontend to fetch and thread a CSRF token through every mutating call
                // for comparatively little benefit on an internal-only tool.
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated()
                )
                // Without these, an anonymous/expired-token request denied right here by
                // anyRequest().authenticated() above never reaches a controller at all —
                // Spring Security's ExceptionTranslationFilter handles it with its own
                // default AuthenticationEntryPoint/AccessDeniedHandler, which return a
                // bare, bodyless 403 with nothing logged server-side. That bypasses
                // GlobalExceptionHandler entirely, so the frontend's 401-triggered
                // silent-refresh-then-logout flow (api.ts's request()) never fires: it
                // gets an unparseable error body and just shows a stuck generic banner
                // instead of logging the user out, even though this is exactly the same
                // "your session is dead" case GlobalExceptionHandler.handleAccessDenied
                // already handles correctly for failures inside a controller. These two
                // handlers make the filter-chain-level gate return the identical
                // ApiError JSON shape (401 for "not authenticated at all", matching
                // handleAccessDenied's convention) so every session-expiry path — not
                // just the ones that happen to reach a controller's @PreAuthorize check
                // — ends up in the same auto-logout flow.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(this::writeUnauthenticated)
                        .accessDeniedHandler(this::writeForbidden)
                )
                // Order matters here: addFilterBefore/After need their anchor class to
                // already have a registered position. apiKeyFilter() must register first
                // (anchored to the built-in UsernamePasswordAuthenticationFilter, which
                // always has a known order) before anything can be positioned relative
                // to ApiKeyFilter.class itself — putting rateLimitFilter's line first
                // threw "The Filter class ApiKeyFilter does not have a registered order"
                // at startup, since ApiKeyFilter hadn't been registered yet at that point.
                .addFilterBefore(apiKeyFilter(), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter(), ApiKeyFilter.class)
                .addFilterAfter(jwtAuthenticationFilter(), ApiKeyFilter.class);
        return http.build();
    }

    // Mirrors GlobalExceptionHandler.handleAccessDenied's UNAUTHORIZED case —
    // this fires for a request the filter chain never let reach a
    // controller at all (no valid session), so it's always "log back in,"
    // never a role/permission distinction (that's method security's job,
    // downstream, already covered by GlobalExceptionHandler).
    private void writeUnauthenticated(HttpServletRequest request, HttpServletResponse response, org.springframework.security.core.AuthenticationException authException) throws IOException {
        writeApiError(response, HttpStatus.UNAUTHORIZED, "Your session has expired. Please sign in again.", request.getRequestURI());
    }

    // Rare in practice — anyRequest().authenticated() above doesn't check
    // roles, so this only fires for the odd case a filter denies an
    // already-authenticated request outright. Kept for symmetry so no path
    // through this filter chain can ever produce the bare, unparseable 403
    // that GlobalExceptionHandler is designed to prevent everywhere else.
    private void writeForbidden(HttpServletRequest request, HttpServletResponse response, org.springframework.security.access.AccessDeniedException accessDeniedException) throws IOException {
        writeApiError(response, HttpStatus.FORBIDDEN, "You don't have permission to do that.", request.getRequestURI());
    }

    private void writeApiError(HttpServletResponse response, HttpStatus status, String message, String path) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        ApiError body = new ApiError(status.value(), status.getReasonPhrase(), message, path);
        objectMapper.writeValue(response.getWriter(), body);
    }

    @Bean
    public RateLimitFilter rateLimitFilter() {
        return new RateLimitFilter(objectMapper);
    }

    @Bean
    public ApiKeyFilter apiKeyFilter() {
        return new ApiKeyFilter(securityProperties, objectMapper);
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtService);
    }

    // AuthService authenticates directly against UserRepository + this encoder (loads the
    // User, checks the hash itself) rather than going through an AuthenticationManager —
    // simpler given there's no Spring Security UserDetailsService in play.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Read from app.security.cors-allowed-origins (APP_CORS_ALLOWED_ORIGINS env
        // var in real use — BACKEND_PLAN.md §11), defaulting to the Vite dev server's
        // origins so local checkouts still work with zero config. Explicit origins
        // (never "*") are required here, not just preferred — a credentialed request
        // (allowCredentials below, needed so the browser will actually send the
        // httpOnly auth cookies) is rejected by every browser if the response's
        // Access-Control-Allow-Origin is a wildcard.
        configuration.setAllowedOrigins(securityProperties.getCorsAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-API-Key"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
