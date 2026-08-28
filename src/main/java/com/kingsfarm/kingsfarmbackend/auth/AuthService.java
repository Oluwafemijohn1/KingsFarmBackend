package com.kingsfarm.kingsfarmbackend.auth;

import com.kingsfarm.kingsfarmbackend.auth.dto.*;
import com.kingsfarm.kingsfarmbackend.common.exception.AccountLockedException;
import com.kingsfarm.kingsfarmbackend.common.exception.BadRequestException;
import com.kingsfarm.kingsfarmbackend.common.exception.InvalidCredentialsException;
import com.kingsfarm.kingsfarmbackend.settings.SecuritySettingsService;
import com.kingsfarm.kingsfarmbackend.user.RefreshToken;
import com.kingsfarm.kingsfarmbackend.user.RefreshTokenRepository;
import com.kingsfarm.kingsfarmbackend.user.User;
import com.kingsfarm.kingsfarmbackend.user.UserRepository;
import com.kingsfarm.kingsfarmbackend.security.AppSecurityProperties;
import com.kingsfarm.kingsfarmbackend.security.JwtService;
import com.kingsfarm.kingsfarmbackend.systemlog.SystemLogService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * All the login/refresh/logout/change-password logic that used to just be
 * "click login" on the frontend demo. Failed-login lockout uses
 * SecuritySettings.lockoutAttempts; once locked, the account stays locked
 * for LOCKOUT_DURATION regardless of further attempts (doesn't extend on
 * every subsequent try, to avoid a trivial DoS against a known username).
 *
 * TODO(Phase 4): once relief_grants exists, a login attempt for a user
 * currently marked "on leave" (active relief grant naming them as the
 * on-leave party) should be rejected here too — deferred per BACKEND_PLAN.md
 * §5.10 since that table doesn't exist yet.
 */
@Service
public class AuthService {

    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SecuritySettingsService securitySettingsService;
    private final AppSecurityProperties securityProperties;
    private final SystemLogService systemLogService;

    public AuthService(UserRepository userRepository,
                        RefreshTokenRepository refreshTokenRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService,
                        SecuritySettingsService securitySettingsService,
                        AppSecurityProperties securityProperties,
                        SystemLogService systemLogService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.securitySettingsService = securitySettingsService;
        this.securityProperties = securityProperties;
        this.systemLogService = systemLogService;
    }

    @Transactional
    public LoginResponse login(LoginRequest request, String ipAddress) {
        User user = userRepository.findByUsername(request.username()).orElse(null);
        if (user == null) {
            systemLogService.logAccess(null, request.username(), "Login", "Failed", ipAddress);
            throw new InvalidCredentialsException("Incorrect username or password.");
        }

        if (!user.isActive()) {
            systemLogService.logAccess(user.getId(), user.getUsername(), "Login", "Failed", ipAddress);
            throw new InvalidCredentialsException("Incorrect username or password.");
        }

        if (user.getLockedUntil() != null) {
            if (user.getLockedUntil().isAfter(Instant.now())) {
                systemLogService.logAccess(user.getId(), user.getUsername(), "Login", "Locked", ipAddress);
                throw new AccountLockedException("This account is temporarily locked due to too many failed login attempts. Try again later.");
            }
            // Lockout window has passed — clear it so this attempt gets a fresh count.
            user.setLockedUntil(null);
            user.setFailedLoginAttempts(0);
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            registerFailedAttempt(user);
            systemLogService.logAccess(user.getId(), user.getUsername(), "Login", "Failed", ipAddress);
            throw new InvalidCredentialsException("Incorrect username or password.");
        }

        user.setFailedLoginAttempts(0);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        systemLogService.logAccess(user.getId(), user.getUsername(), "Login", "Success", ipAddress);

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getUsername(), user.getRole(), user.isMustChangePassword());
        String refreshToken = issueRefreshToken(user);

        return new LoginResponse(
                accessToken, refreshToken,
                user.getId(), user.getUsername(), user.getFullName(),
                user.getRole(), user.getRole().label(),
                user.isMustChangePassword()
        );
    }

    private void registerFailedAttempt(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        int threshold = securitySettingsService.get().getLockoutAttempts();
        if (attempts >= threshold) {
            user.setLockedUntil(Instant.now().plus(LOCKOUT_DURATION));
        }
        userRepository.save(user);
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        String hash = jwtService.hashRefreshToken(request.refreshToken());
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid or expired refresh token."));

        if (!existing.isActive()) {
            throw new InvalidCredentialsException("Invalid or expired refresh token.");
        }

        User user = existing.getUser();
        if (!user.isActive()) {
            throw new InvalidCredentialsException("Invalid or expired refresh token.");
        }

        // Rotate: revoke the used token, issue a brand new one — a stolen refresh
        // token that gets reused after rotation signals compromise, but a single
        // active token per session is a deliberate simplicity tradeoff for now.
        existing.setRevokedAt(Instant.now());
        refreshTokenRepository.save(existing);

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getUsername(), user.getRole(), user.isMustChangePassword());
        String newRefreshToken = issueRefreshToken(user);
        return new TokenResponse(accessToken, newRefreshToken);
    }

    @Transactional
    public void logout(LogoutRequest request, String ipAddress) {
        String hash = jwtService.hashRefreshToken(request.refreshToken());
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            token.setRevokedAt(Instant.now());
            refreshTokenRepository.save(token);
            User user = token.getUser();
            systemLogService.logAccess(user.getId(), user.getUsername(), "Logout", "Success", ipAddress);
        });
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("Session is no longer valid."));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Current password is incorrect.");
        }

        int minLength = securitySettingsService.get().getPasswordMinLength();
        if (request.newPassword().length() < minLength) {
            throw new BadRequestException("New password must be at least " + minLength + " characters.");
        }
        if (request.newPassword().equals(request.currentPassword())) {
            throw new BadRequestException("New password must be different from the current password.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);
    }

    private String issueRefreshToken(User user) {
        String raw = jwtService.generateRawRefreshToken();
        Instant now = Instant.now();
        RefreshToken token = RefreshToken.builder()
                .user(user)
                .tokenHash(jwtService.hashRefreshToken(raw))
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofDays(securityProperties.getJwt().getRefreshTokenDays())))
                .build();
        refreshTokenRepository.save(token);
        return raw;
    }
}
