package com.kingsfarm.kingsfarmbackend.user;

import com.kingsfarm.kingsfarmbackend.common.exception.BadRequestException;
import com.kingsfarm.kingsfarmbackend.settings.SecuritySettingsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/** Issues and redeems the emailed verify-email / set-password links. */
@Service
public class AccountSetupService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountTokenRepository tokens;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final SecuritySettingsService securitySettings;
    private final EmailService emailService;
    private final int tokenHours;

    public AccountSetupService(AccountTokenRepository tokens, UserRepository users, PasswordEncoder passwordEncoder,
                               SecuritySettingsService securitySettings, EmailService emailService,
                               @Value("${app.mail.token-hours:48}") int tokenHours) {
        this.tokens = tokens;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.securitySettings = securitySettings;
        this.emailService = emailService;
        this.tokenHours = tokenHours;
    }

    /** Invalidates any earlier links for the user, mints a new one and emails it. @return whether the email was sent. */
    @Transactional
    public boolean issueAndSend(User user, boolean passwordReset) {
        tokens.deleteAllForUser(user);
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        AccountToken t = new AccountToken();
        t.setUser(user);
        t.setTokenHash(hash(raw));
        t.setPurpose(passwordReset ? AccountTokenPurpose.RESET_PASSWORD : AccountTokenPurpose.VERIFY_EMAIL);
        t.setExpiresAt(Instant.now().plus(Duration.ofHours(tokenHours)));
        tokens.save(t);
        return emailService.sendAccountSetup(user, raw, passwordReset, tokenHours);
    }

    /** Lets the setup page show who the link is for before they type a password. */
    @Transactional(readOnly = true)
    public AccountToken inspect(String rawToken) {
        AccountToken t = tokens.findByTokenHash(hash(rawToken)).orElse(null);
        if (t == null || !t.isUsable() || !t.getUser().isActive()) {
            throw new BadRequestException("This link is invalid or has expired. Ask your administrator to send a new one.");
        }
        return t;
    }

    @Transactional
    public void complete(String rawToken, String newPassword) {
        AccountToken t = inspect(rawToken);
        int min = securitySettings.get().getPasswordMinLength();
        if (newPassword == null || newPassword.length() < min) {
            throw new BadRequestException("Password must be at least " + min + " characters.");
        }
        User user = t.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setEmailVerified(true);
        user.setMustChangePassword(false);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        users.save(user);
        t.setUsedAt(Instant.now());
        tokens.save(t);
    }

    private static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
