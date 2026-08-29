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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One (feedType, ingredient) pair's "qty per ton" — sparse, only pairs that
 * actually appear in that feed type's formulation get a row. {@code qtyPerTon}
 * is the live value, edited immediately on every keystroke in the frontend
 * (no separate draft state server-side, same simplification as everywhere
 * else). {@code lastSavedQtyPerTon} is this backend's durable substitute for
 * the frontend's ephemeral {@code formBaseline} React state — it only moves
 * when "Save Formulation" runs (see FeedMillService.saveFormulation), and is
 * what that action diffs against to build a
 * {@link FeedFormulationHistoryGroup}. Always stored in the ingredient's own
 * base unit ({@code ingredient.unit}) — kg/g display conversion is a
 * frontend-only concern, never persisted.
 */
@Entity
@Table(name = "feed_formulation_entries", uniqueConstraints = @UniqueConstraint(columnNames = {"feed_type_id", "ingredient_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedFormulationEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feed_type_id", nullable = false)
    private FeedType feedType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private FeedIngredient ingredient;

    @Column(name = "qty_per_ton", nullable = false)
    @Builder.Default
    private double qtyPerTon = 0;

    @Column(name = "last_saved_qty_per_ton", nullable = false)
    @Builder.Default
    private double lastSavedQtyPerTon = 0;
}
