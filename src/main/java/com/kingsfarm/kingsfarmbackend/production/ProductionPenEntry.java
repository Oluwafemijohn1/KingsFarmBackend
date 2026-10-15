package com.kingsfarm.kingsfarmbackend.production;

import com.kingsfarm.kingsfarmbackend.birdstock.Pen;
import com.kingsfarm.kingsfarmbackend.common.CatKey;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.EnumMap;
import java.util.Map;

/**
 * One row per (pen, day) — "Production by Pen" in ProductionView. Flat
 * columns per category rather than one child row per category (unlike Whole
 * Egg's line items in the plan): every pen produces across all six
 * categories every day, so this is a dense matrix, not a sparse one — flat
 * columns are simpler to query and match how BirdPenRecord was modeled.
 */
@Entity
@Table(name = "production_pen_entries", uniqueConstraints = @UniqueConstraint(columnNames = {"pen_id", "entry_date"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductionPenEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pen_id", nullable = false)
    private Pen pen;

    @Column(name = "entry_date", nullable = false)
    private java.time.LocalDate entryDate;

    // double, not int: pen production is entered by crate, and a partial
    // crate (e.g. 1.5) is a real, valid figure — see set()/get()/total()
    // below and ProductionService's javadoc for how this precision carries
    // through Category Stock Summary and the Reports tab.
    @Column(name = "qty_xl", nullable = false)
    @Builder.Default
    private double qtyXl = 0;

    @Column(name = "qty_lg", nullable = false)
    @Builder.Default
    private double qtyLg = 0;

    @Column(name = "qty_md", nullable = false)
    @Builder.Default
    private double qtyMd = 0;

    @Column(name = "qty_sm", nullable = false)
    @Builder.Default
    private double qtySm = 0;

    @Column(name = "qty_pl", nullable = false)
    @Builder.Default
    private double qtyPl = 0;

    @Column(name = "qty_wh", nullable = false)
    @Builder.Default
    private double qtyWh = 0;

    @Column(name = "entered_by", length = 64)
    private String enteredBy;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    public double get(CatKey category) {
        return switch (category) {
            case X_LARGE -> qtyXl;
            case LARGE -> qtyLg;
            case MEDIUM -> qtyMd;
            case SMALL -> qtySm;
            case PULLET -> qtyPl;
            case WHITE -> qtyWh;
        };
    }

    public void set(CatKey category, double value) {
        switch (category) {
            case X_LARGE -> qtyXl = value;
            case LARGE -> qtyLg = value;
            case MEDIUM -> qtyMd = value;
            case SMALL -> qtySm = value;
            case PULLET -> qtyPl = value;
            case WHITE -> qtyWh = value;
        }
    }

    public Map<CatKey, Double> asMap() {
        Map<CatKey, Double> map = new EnumMap<>(CatKey.class);
        for (CatKey k : CatKey.values()) {
            map.put(k, get(k));
        }
        return map;
    }

    public double total() {
        return qtyXl + qtyLg + qtyMd + qtySm + qtyPl + qtyWh;
    }
}
