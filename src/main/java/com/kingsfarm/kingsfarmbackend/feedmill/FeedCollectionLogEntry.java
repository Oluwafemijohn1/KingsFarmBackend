package com.kingsfarm.kingsfarmbackend.feedmill;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * One Fish Feed Collection record — mirrors {@code CollectionLogEntry} in
 * FeedMillView.tsx. Same-day per-entry edit lock (via occurredAt). Editing a
 * same-day entry reconciles the delta straight back into
 * {@link FishFeedStock#getCollected()} for all three types at once — exactly
 * like {@code updateCollectionEntry} in the frontend, and deliberately
 * without any stock-availability validation on edit (the frontend doesn't
 * validate there either, only on the original {@code submitCollection}).
 */
@Entity
@Table(name = "feed_collection_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedCollectionLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "collected_by", nullable = false, length = 16)
    private FeedCollector collectedBy;

    @Column(name = "fish_starter_kg", nullable = false)
    @Builder.Default
    private double fishStarterKg = 0;

    @Column(name = "fish_grower_kg", nullable = false)
    @Builder.Default
    private double fishGrowerKg = 0;

    @Column(name = "fish_finisher_kg", nullable = false)
    @Builder.Default
    private double fishFinisherKg = 0;

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

    public double total() {
        return fishStarterKg + fishGrowerKg + fishFinisherKg;
    }
}
