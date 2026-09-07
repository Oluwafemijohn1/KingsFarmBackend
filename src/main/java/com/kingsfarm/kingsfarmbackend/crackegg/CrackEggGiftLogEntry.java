package com.kingsfarm.kingsfarmbackend.crackegg;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A permanent snapshot taken when the manager clicks "Save Gift" — captures
 * whatever CrackEggState's gcGiftQty/gcGiftRecipient/gcGiftAuthorizer were
 * at that moment. Editing a past entry here never changes the live
 * CrackEggState value, matching the frontend's giftLog exactly.
 */
@Entity
@Table(name = "gc_gift_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CrackEggGiftLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private double qty;

    @Column(length = 255)
    private String recipient;

    @Column(length = 255)
    private String authorizer;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "entered_by", length = 64)
    private String enteredBy;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @PrePersist
    void onCreate() {
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }
}
