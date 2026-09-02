package com.kingsfarm.kingsfarmbackend.auth;

import com.kingsfarm.kingsfarmbackend.security.AppSecurityProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Builds and reads the two httpOnly auth cookies (BACKEND_PLAN.md §11 decision
 * #2 — chosen over localStorage for XSS protection). Both are httpOnly +
 * SameSite=Strict + (by default) Secure. SameSite=Strict is the CSRF
 * mitigation here instead of Spring Security's token-based CSRF machinery
 * (disabled in SecurityConfig): a Strict cookie is never attached to a
 * request that didn't originate from a same-site page, which covers the
 * cross-site-forged-request threat model without needing the frontend to
 * fetch and thread a CSRF token through every mutating call.
 * <p>
 * The access cookie is scoped to {@code /} (every endpoint needs it); the
 * refresh cookie is scoped to {@code /api/v1/auth} only — it's never read by
 * any endpoint outside that controller, so there's no reason for the browser
 * to attach it anywhere else.
 */
@Component
public class AuthCookies {

    public static final String ACCESS_COOKIE = "kf_access_token";
    public static final String REFRESH_COOKIE = "kf_refresh_token";
    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

    private final AppSecurityProperties properties;

    public AuthCookies(AppSecurityProperties properties) {
        this.properties = properties;
    }

    public void setAuthCookies(HttpServletResponse response, String accessToken, String refreshToken) {
        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie(accessToken).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(refreshToken).toString());
    }

    public void clearAuthCookies(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, expired(ACCESS_COOKIE, "/").toString());
        response.addHeader(HttpHeaders.SET_COOKIE, expired(REFRESH_COOKIE, REFRESH_COOKIE_PATH).toString());
    }

    public static String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private ResponseCookie accessCookie(String token) {
        return ResponseCookie.from(ACCESS_COOKIE, token)
                .httpOnly(true)
                .secure(properties.isCookieSecure())
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ofMinutes(properties.getJwt().getAccessTokenMinutes()))
                .build();
    }

    private ResponseCookie refreshCookie(String token) {
        return ResponseCookie.from(REFRESH_COOKIE, token)
                .httpOnly(true)
                .secure(properties.isCookieSecure())
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(Duration.ofDays(properties.getJwt().getRefreshTokenDays()))
                .build();
    }

    private ResponseCookie expired(String name, String path) {
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(properties.isCookieSecure())
                .sameSite("Strict")
                .path(path)
                .maxAge(Duration.ZERO)
                .build();
    }
}
