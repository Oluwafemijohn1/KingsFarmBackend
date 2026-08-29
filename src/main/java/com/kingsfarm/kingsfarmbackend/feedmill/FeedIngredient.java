package com.kingsfarm.kingsfarmbackend.feedmill;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Catalog entry AND always-current running stock in one row — unlike Bird
 * Stock/Production/Mortality's per-pen-per-day entries, the Ingredient
 * Inventory table has no day dimension at all in the frontend (no daily
 * reset, no carry-forward step shown anywhere); it behaves exactly like
 * Whole Egg's {@code WeCategoryValue} / Crack Egg's {@code CrackEggState} —
 * a single always-current row per name (BACKEND_PLAN.md §5.3). Opening is
 * opening-stock-locked (module=FEED_MILL, scope=name, matching
 * {@code OpeningStockCell}'s {@code scope={r.name}} in FeedMillView.tsx
 * exactly). Added, Unit, and Min are freely editable, no lock. Used is
 * never directly editable by a manager — only auto-deducted by
 * {@code FeedMillService.runProduction}/{@code updateProductionEntry}.
 * Quantities are {@code double}, not {@code int}, because formulation math
 * (qty-per-ton × tons produced) routinely produces fractional kg/g amounts.
 */
@Entity
@Table(name = "feed_ingredients", uniqueConstraints = @UniqueConstraint(columnNames = "name"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 8)
    @Builder.Default
    private String unit = "kg";

    @Column(nullable = false)
    @Builder.Default
    private double opening = 0;

    @Column(nullable = false)
    @Builder.Default
    private double added = 0;

    @Column(nullable = false)
    @Builder.Default
    private double used = 0;

    @Column(nullable = false)
    @Builder.Default
    private double min = 0;

    @Column(name = "entered_by", length = 64)
    private String enteredBy;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    public double closing() {
        return opening + added - used;
    }

    public boolean low() {
        return closing() < min;
    }
}
