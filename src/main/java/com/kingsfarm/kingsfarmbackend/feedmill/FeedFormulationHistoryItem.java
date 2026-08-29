package com.kingsfarm.kingsfarmbackend.feedmill;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One changed ingredient within a {@link FeedFormulationHistoryGroup} — previous value (its lastSavedQtyPerTon before this save) and new value (its qtyPerTon at the moment of save). */
@Entity
@Table(name = "feed_formulation_history_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedFormulationHistoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private FeedFormulationHistoryGroup group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private FeedIngredient ingredient;

    @Column(name = "old_val", nullable = false)
    private double oldVal;

    @Column(name = "new_val", nullable = false)
    private double newVal;
}
