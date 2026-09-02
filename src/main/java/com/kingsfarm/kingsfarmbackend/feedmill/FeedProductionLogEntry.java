package com.kingsfarm.kingsfarmbackend.feedmill;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One production run — mirrors {@code ProductionLogEntry} in
 * FeedMillView.tsx. Same-day per-entry edit lock (via occurredAt), same
 * pattern as Whole Egg/Crack Egg/Mortality's sale rows, not a whole-day
 * record lock. Editing type/qty on the same day it was recorded reconciles
 * the ingredient usage delta (and, for fish feed types, the Fish Feed Stock
 * "added" delta) exactly the way FeedMillService.updateProductionEntry
 * replicates the frontend's {@code updateProductionEntry}.
 * <p>
 * {@code formulationRef} mirrors the frontend's {@code form: "FM-2026-03"}
 * field, but that value is static placeholder text in the frontend itself —
 * there is no real formulation-versioning system behind it (every row, old
 * and new, uses the same hardcoded string). Rather than invent a fake
 * numbering scheme with no real backing logic, this is kept as an optional,
 * un-generated field — nullable, never auto-populated by this service.
 */
@Entity
@Table(name = "feed_production_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedProductionLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feed_type_id", nullable = false)
    private FeedType feedType;

    @Column(name = "qty_tons", nullable = false)
    private double qtyTons;

    @Column(name = "formulation_ref", length = 32)
    private String formulationRef;

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
