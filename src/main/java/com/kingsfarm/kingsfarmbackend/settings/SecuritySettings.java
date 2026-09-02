package com.kingsfarm.kingsfarmbackend.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Singleton row (id is always 1) mirroring the frontend's Admin → Security
 * Settings panel. Read on every login (lockout, session timeout) and every
 * change-password call (min length), so kept as one small cached-friendly
 * row rather than a generic key/value settings table.
 */
@Entity
@Table(name = "security_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SecuritySettings {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    @Column(name = "session_timeout_minutes", nullable = false)
    private int sessionTimeoutMinutes = 60;

    @Column(name = "lockout_attempts", nullable = false)
    private int lockoutAttempts = 5;

    @Column(name = "password_min_length", nullable = false)
    private int passwordMinLength = 8;
}
