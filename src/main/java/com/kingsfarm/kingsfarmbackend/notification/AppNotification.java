package com.kingsfarm.kingsfarmbackend.notification;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** One in-app notification for one person (what the bell shows). {@code link} is the front-end page it opens. */
@Entity
@Table(name = "app_notification", indexes = @Index(name = "idx_notif_recipient", columnList = "recipient"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The recipient's username (their sign-in email). */
    @Column(nullable = false, length = 128)
    private String recipient;

    /** REQUEST, REQUEST_RESULT, PAYMENT, LOW_STOCK ... - used for the icon and for not repeating the same alert. */
    @Column(nullable = false, length = 32)
    private String type;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(length = 255)
    private String link;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
