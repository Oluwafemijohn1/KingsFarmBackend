package com.kingsfarm.kingsfarmbackend.birdstock;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * The pen catalog — separate from {@link BirdPenRecord} (the daily entries)
 * so a pen's identity persists across days and "removing" a pen never
 * deletes its history. Removal is a soft deactivate (BACKEND_PLAN.md never
 * specifies hard-deleting farm records, and every other module treats
 * historical rows as permanent).
 */
@Entity
@Table(name = "pens", uniqueConstraints = @UniqueConstraint(columnNames = "name"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
