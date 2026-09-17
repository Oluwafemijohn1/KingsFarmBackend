package com.kingsfarm.kingsfarmbackend.security;

import com.kingsfarm.kingsfarmbackend.user.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Issues and verifies the short-lived HS256 access tokens. Refresh tokens
 * are a different, simpler thing (see AuthService/RefreshToken) — a random
 * opaque string whose hash is stored server-side, not a JWT, since it needs
 * to be revocable and a signed JWT can't be un-issued before it expires.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private static final String CLAIM_USER_ID = "uid";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_MUST_CHANGE_PASSWORD = "mcp";
    private static final String CLAIM_EXTRA_ROLES = "extraRoles";

    private final SecretKey signingKey;
    private final Duration accessTokenTtl;

    public JwtService(AppSecurityProperties properties) {
        this.signingKey = Keys.hmacShaKeyFor(properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = Duration.ofMinutes(properties.getJwt().getAccessTokenMinutes());
    }

    public String generateAccessToken(Long userId, String username, Role role, boolean mustChangePassword) {
        return generateAccessToken(userId, username, role, mustChangePassword, List.of());
    }

    /** {@code extraRoles} bakes in any Relief Access grants active for this user as the relieving officer at issuance time — see AuthenticatedPrincipal's javadoc for the staleness tradeoff this accepts. */
    public String generateAccessToken(Long userId, String username, Role role, boolean mustChangePassword, List<Role> extraRoles) {
        Instant now = Instant.now();
        List<String> extraRoleNames = extraRoles.stream().map(Role::name).toList();
        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_ROLE, role.name())
                .claim(CLAIM_MUST_CHANGE_PASSWORD, mustChangePassword)
                .claim(CLAIM_EXTRA_ROLES, extraRoleNames)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Returns null (rather than throwing) for any expired/malformed/
     * invalid-signature token — callers just treat that as "not
     * authenticated", which is right for the response the client gets. But
     * "why" used to be thrown away completely, so a burst of 401s was
     * indistinguishable in the logs from a quiet afternoon: expired (the
     * routine case — this is exactly what the frontend's silent-refresh
     * flow exists to paper over, so DEBUG, not a real event) vs malformed/
     * bad-signature/unsupported (rare, and worth a closer look — could be a
     * tampered token, a client/server signing-key mismatch after a secret
     * rotation, or a genuine bug) get logged at different levels so the
     * routine case doesn't bury the ones actually worth noticing.
     */
    public AuthenticatedPrincipal parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Long userId = claims.get(CLAIM_USER_ID, Long.class);
            Role role = Role.valueOf(claims.get(CLAIM_ROLE, String.class));
            boolean mustChangePassword = Boolean.TRUE.equals(claims.get(CLAIM_MUST_CHANGE_PASSWORD, Boolean.class));
            @SuppressWarnings("unchecked")
            List<String> extraRoleNames = claims.get(CLAIM_EXTRA_ROLES, List.class);
            List<Role> extraRoles = extraRoleNames == null ? List.of()
                    : extraRoleNames.stream().map(Role::valueOf).toList();
            return new AuthenticatedPrincipal(userId, claims.getSubject(), role, mustChangePassword, extraRoles);
        } catch (ExpiredJwtException ex) {
            log.debug("Access token expired for subject '{}' at {}", ex.getClaims() != null ? ex.getClaims().getSubject() : "?", ex.getClaims() != null ? ex.getClaims().getExpiration() : "?");
            return null;
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("Access token rejected — {}: {}", ex.getClass().getSimpleName(), ex.getMessage());
            return null;
        }
    }

    // ── Refresh tokens ──────────────────────────────────────────────────────
    // Opaque random strings (not JWTs) so they can be revoked server-side —
    // see RefreshToken/RefreshTokenRepository. Only the SHA-256 hash is ever
    // persisted or looked up; the raw value is returned to the caller once
    // and never stored.

    public String generateRawRefreshToken() {
        return UUID.randomUUID() + "." + UUID.randomUUID();
    }

    public String hashRefreshToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is guaranteed available on every JVM — this never actually happens.
            throw new IllegalStateException(ex);
        }
    }
}
