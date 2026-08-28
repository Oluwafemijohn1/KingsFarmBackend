package com.kingsfarm.kingsfarmbackend.security;

import com.kingsfarm.kingsfarmbackend.user.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Issues and verifies the short-lived HS256 access tokens. Refresh tokens
 * are a different, simpler thing (see AuthService/RefreshToken) — a random
 * opaque string whose hash is stored server-side, not a JWT, since it needs
 * to be revocable and a signed JWT can't be un-issued before it expires.
 */
@Service
public class JwtService {

    private static final String CLAIM_USER_ID = "uid";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_MUST_CHANGE_PASSWORD = "mcp";

    private final SecretKey signingKey;
    private final Duration accessTokenTtl;

    public JwtService(AppSecurityProperties properties) {
        this.signingKey = Keys.hmacShaKeyFor(properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = Duration.ofMinutes(properties.getJwt().getAccessTokenMinutes());
    }

    public String generateAccessToken(Long userId, String username, Role role, boolean mustChangePassword) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_ROLE, role.name())
                .claim(CLAIM_MUST_CHANGE_PASSWORD, mustChangePassword)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(signingKey)
                .compact();
    }

    /** Returns null (rather than throwing) for any expired/malformed/invalid-signature token — callers just treat that as "not authenticated". */
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
            return new AuthenticatedPrincipal(userId, claims.getSubject(), role, mustChangePassword);
        } catch (JwtException | IllegalArgumentException ex) {
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
