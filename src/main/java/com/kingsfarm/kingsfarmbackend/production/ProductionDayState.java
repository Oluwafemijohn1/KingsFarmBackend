package com.kingsfarm.kingsfarmbackend.production;

import com.kingsfarm.kingsfarmbackend.common.CatKey;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

/**
 * One row per day — every scalar field on ProductionView's Whole Egg tab
 * (Category Stock Summary's Opening Stock) and Crack Egg tab (open/prod/
 * classify figures) that isn't per-pen. Mirrors the frontend's single Save
 * Record lock covering "the whole Production Record (both Whole Egg and
 * Crack Egg tabs)" — see ProductionService for the same-day edit rule.
 *
 * Note: crackGoodOpen/crackRoughOpen are NOT run through
 * OpeningStockLockService, even though their UI copy says "auto-filled from
 * yesterday" — the frontend wraps catOpening in <OpeningStockCell/> but uses
 * a plain <NumInput/> for these two, so they're carried forward daily (see
 * ProductionService) but never subject to the admin-approval lock. Preserved
 * exactly as-is rather than "fixed," per the instruction to keep existing
 * frontend behavior unchanged.
 */
@Entity
@Table(name = "production_day_state", uniqueConstraints = @UniqueConstraint(columnNames = "entry_date"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductionDayState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "cat_opening_xl", nullable = false)
    @Builder.Default
    private int catOpeningXl = 0;

    @Column(name = "cat_opening_lg", nullable = false)
    @Builder.Default
    private int catOpeningLg = 0;

    @Column(name = "cat_opening_md", nullable = false)
    @Builder.Default
    private int catOpeningMd = 0;

    @Column(name = "cat_opening_sm", nullable = false)
    @Builder.Default
    private int catOpeningSm = 0;

    @Column(name = "cat_opening_pl", nullable = false)
    @Builder.Default
    private int catOpeningPl = 0;

    @Column(name = "cat_opening_wh", nullable = false)
    @Builder.Default
    private int catOpeningWh = 0;

    @Column(name = "crack_good_open", nullable = false)
    @Builder.Default
    private int crackGoodOpen = 0;

    @Column(name = "crack_rough_open", nullable = false)
    @Builder.Default
    private int crackRoughOpen = 0;

    @Column(name = "crack_good_prod", nullable = false)
    @Builder.Default
    private int crackGoodProd = 0;

    @Column(name = "crack_rough_prod", nullable = false)
    @Builder.Default
    private int crackRoughProd = 0;

    @Column(name = "good_classify", nullable = false)
    @Builder.Default
    private double goodClassify = 0;

    @Column(name = "rough_classify", nullable = false)
    @Builder.Default
    private double roughClassify = 0;

    @Column(name = "entered_by", length = 64)
    private String enteredBy;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    public int catOpening(CatKey category) {
        return switch (category) {
            case X_LARGE -> catOpeningXl;
            case LARGE -> catOpeningLg;
            case MEDIUM -> catOpeningMd;
            case SMALL -> catOpeningSm;
            case PULLET -> catOpeningPl;
            case WHITE -> catOpeningWh;
        };
    }

    public void setCatOpening(CatKey category, int value) {
        switch (category) {
            case X_LARGE -> catOpeningXl = value;
            case LARGE -> catOpeningLg = value;
            case MEDIUM -> catOpeningMd = value;
            case SMALL -> catOpeningSm = value;
            case PULLET -> catOpeningPl = value;
            case WHITE -> catOpeningWh = value;
        }
    }

    public Map<CatKey, Integer> catOpeningMap() {
        Map<CatKey, Integer> map = new EnumMap<>(CatKey.class);
        for (CatKey k : CatKey.values()) {
            map.put(k, catOpening(k));
        }
        return map;
    }
}
