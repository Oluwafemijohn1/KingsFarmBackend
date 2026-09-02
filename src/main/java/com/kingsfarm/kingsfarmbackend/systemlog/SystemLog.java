package com.kingsfarm.kingsfarmbackend.systemlog;

import com.kingsfarm.kingsfarmbackend.common.Mod;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One row per login attempt, per user action, per audited change — replaces
 * AdminView's three static demo arrays (ACCESS_LOG/ACTIVITY_LOG/AUDIT) with
 * real rows written as things actually happen (BACKEND_PLAN.md §5.9).
 * `username` is a plain snapshot string (not just the FK) because a failed
 * login for a username that doesn't exist has no User row to point at.
 */
@Entity
@Table(
        name = "system_logs",
        indexes = @Index(name = "idx_system_logs_type_time", columnList = "log_type, occurred_at")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "log_type", nullable = false, length = 16)
    private LogType logType;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "user_id")
    private Long userId;

    @Column(length = 64)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private Mod module;

    @Column(nullable = false, length = 128)
    private String action;

    @Column(length = 1000)
    private String detail;

    /** e.g. "Success"/"Failed" for ACCESS rows; unused for ACTIVITY/AUDIT. */
    @Column(length = 32)
    private String status;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @PrePersist
    void onCreate() {
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }
}
