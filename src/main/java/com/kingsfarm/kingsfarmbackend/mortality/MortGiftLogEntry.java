package com.kingsfarm.kingsfarmbackend.mortality;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A permanent record of one "Save Gifts" action — captures the Good/Dry/Runt
 * quantities gifted together with a shared recipient/authorizer, exactly like
 * {@code giftLog} in MortalityView.tsx. Unlike Crack Egg's gift log (which
 * snapshots a separately-live {@code gcGiftQty}), Mortality's gift form has
 * no "Save"-independent live state — the qty/recipient/authorizer are pure
 * form-local values until "Save Gifts" is clicked, at which point they're
 * written here AND into {@link MortCategoryValue#getGiftQty()} in the same
 * action (see MortalityService.saveGift).
 */
@Entity
@Table(name = "mort_gift_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MortGiftLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int good;

    @Column(nullable = false)
    private int dry;

    @Column(nullable = false)
    private int runt;

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
