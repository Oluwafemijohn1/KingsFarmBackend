package com.kingsfarm.kingsfarmbackend.mortality;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * One row per {@link MortCat} — the always-current "Opening" and "Gift"
 * values shown on the Stock Overview table. Same running-total pattern as
 * Whole Egg's {@code WeCategoryValue} and Crack Egg's {@code CrackEggState}
 * (BACKEND_PLAN.md §5.3): not a per-day snapshot. Everything else on that
 * table is deliberately NOT stored here because it's cheaper/safer to
 * compute live and never risk drifting out of sync:
 * <ul>
 *   <li>Produced — sum of today's {@link MortPenEntry} rows for this
 *   category (mirrors ProductionService.catProdTotals).</li>
 *   <li>Sales — sum of every {@link MortSaleEntry} ever recorded for this
 *   category (mirrors CrackEggService.goodSalesQty).</li>
 *   <li>Catfish/Disposal — live on {@link MortCatfishDisposalState} instead,
 *   since those two share one same-day save action across categories.</li>
 * </ul>
 * Opening is opening-stock-locked (module=MORTALITY, scope=category wire).
 * Gift is overwritten (not accumulated) each time "Save Gifts" runs — see
 * MortalityService.saveGift — matching the frontend's {@code saveGifts()},
 * which replaces rather than adds to the category's live gift total.
 */
@Entity
@Table(name = "mort_category_values", uniqueConstraints = @UniqueConstraint(columnNames = "category"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MortCategoryValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MortCat category;

    @Column(nullable = false)
    @Builder.Default
    private int opening = 0;

    @Column(name = "gift_qty", nullable = false)
    @Builder.Default
    private int giftQty = 0;
}
