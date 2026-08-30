package com.kingsfarm.kingsfarmbackend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kingsfarm.kingsfarmbackend.common.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory, per-IP, fixed-window rate limiter (Phase 6). No external
 * dependency (Bucket4j etc. would need a Maven dependency this environment
 * has no way to resolve or compile-verify) — good enough for a single-
 * instance internal tool with a small, known set of users; would need a
 * shared store (e.g. Redis) if this ever runs as more than one instance.
 * <p>
 * Two windows: a strict one on {@code POST /api/v1/auth/login} — brute-force
 * protection that {@link com.kingsfarm.kingsfarmbackend.auth.AuthService}'s
 * per-account lockout doesn't cover on its own, since that only engages once
 * a *known* username has racked up failed attempts (see AuthService's
 * javadoc); an attacker cycling through usernames, or just hammering with a
 * wrong one, hits no lockout there at all. And a generous one on every other
 * endpoint, as a general-abuse backstop that normal use should never feel.
 * <p>
 * Fixed windows, not sliding — simple, and precise enough for what this is
 * defending against. The increment-then-compare below has a small benign
 * race right at a window boundary (two requests can both read the same
 * about-to-expire window and both get counted against it) — acceptable for
 * abuse mitigation, not something that needs full CAS/lock precision.
 * <p>
 * Positioned first in the filter chain (before {@link ApiKeyFilter}), so a
 * flood is rejected before the API-key check, JWT parsing, or the database
 * are ever touched.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int LOGIN_WINDOW_LIMIT = 10;
    private static final int GLOBAL_WINDOW_LIMIT = 120;
    private static final long WINDOW_MILLIS = 60_000;
    private static final String LOGIN_PATH = "/api/v1/auth/login";

    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Window> loginWindows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Window> globalWindows = new ConcurrentHashMap<>();

    public RateLimitFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ip = clientIp(request);
        boolean isLogin = "POST".equalsIgnoreCase(request.getMethod()) && LOGIN_PATH.equals(request.getRequestURI());

        if (isLogin && !allow(loginWindows, ip, LOGIN_WINDOW_LIMIT)) {
            writeTooManyRequests(request, response, "Too many login attempts. Please wait a minute and try again.");
            return;
        }
        if (!allow(globalWindows, ip, GLOBAL_WINDOW_LIMIT)) {
            writeTooManyRequests(request, response, "Too many requests. Please slow down.");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean allow(ConcurrentHashMap<String, Window> windows, String key, int limit) {
        long now = System.currentTimeMillis();
        Window window = windows.compute(key, (k, existing) ->
                (existing == null || now - existing.windowStart >= WINDOW_MILLIS) ? new Window(now) : existing);
        return window.count.incrementAndGet() <= limit;
    }

    private String clientIp(HttpServletRequest request) {
        // Only meaningful once a reverse proxy actually terminates TLS in front
        // of this app and sets this header itself — falls back to the socket
        // address, which is all local/dev setups without a proxy ever have.
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeTooManyRequests(HttpServletRequest request, HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError body = new ApiError(HttpStatus.TOO_MANY_REQUESTS.value(), "Too Many Requests", message, request.getRequestURI());
        objectMapper.writeValue(response.getWriter(), body);
    }

    private static final class Window {
        final long windowStart;
        final AtomicInteger count = new AtomicInteger(0);

        Window(long windowStart) {
            this.windowStart = windowStart;
        }
    }
}
