package com.kingsfarm.kingsfarmbackend.user;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A login account. Every account is created by an Administrator from inside
 * the app (see UserController) — there is no public sign-up endpoint,
 * matching the frontend's "internal application only" requirement. Multiple
 * accounts can share the same {@link Role}; module data is scoped by module,
 * not by individual user, so e.g. two Feed Mill Managers see each other's
 * records.
 */
@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(columnNames = "username"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Role role;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /** Set true whenever an admin (re)sets this account's password; cleared on a successful change-password call. */
    @Column(name = "must_change_password", nullable = false)
    @Builder.Default
    private boolean mustChangePassword = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Username snapshot of whichever Administrator created this account — null only for the bootstrap admin. */
    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "failed_login_attempts", nullable = false)
    @Builder.Default
    private int failedLoginAttempts = 0;

    /** Non-null while the account is locked out from too many failed attempts (see SecuritySettings.lockoutAttempts). */
    @Column(name = "locked_until")
    private Instant lockedUntil;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
