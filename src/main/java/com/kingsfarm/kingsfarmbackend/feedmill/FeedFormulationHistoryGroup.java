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
 * One "Save Formulation" click that changed at least one ingredient — mirrors
 * FeedMillView.tsx's {@code FormHistoryGroup}: one row per save, holding
 * every ingredient that changed in that save, rather than one row per
 * ingredient (which the frontend's own comment says "got unreadable fast").
 * Only created when {@code saveFormulation} finds at least one changed
 * ingredient — a no-op save (nothing changed) creates no group, matching the
 * frontend's {@code if (changedIngredients.length > 0)} guard.
 */
@Entity
@Table(name = "feed_formulation_history_groups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedFormulationHistoryGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feed_type_id", nullable = false)
    private FeedType feedType;

    @Column(name = "changed_by", length = 64)
    private String changedBy;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @PrePersist
    void onCreate() {
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }
}
