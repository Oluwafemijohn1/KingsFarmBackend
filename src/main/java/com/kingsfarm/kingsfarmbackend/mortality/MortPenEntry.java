package com.kingsfarm.kingsfarmbackend.mortality;

import com.kingsfarm.kingsfarmbackend.birdstock.Pen;
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

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

/**
 * One row per (pen, day) — "Pen Mortality Entry" on the Record Mortality tab.
 * Flat columns per category, same reasoning as {@code ProductionPenEntry}
 * (every pen reports across all five categories every day — dense, not
 * sparse). Reuses the same {@link Pen} catalog as Bird Stock/Production
 * (confirmed against the frontend: BirdStockView, ProductionView, and
 * MortalityView all list the same five pens — Pen A1/A2/A3/B1/B2).
 * <p>
 * Deliberately independent of {@code BirdPenRecord.mortality} even though
 * both are called "mortality" — the frontend keeps two separate, never-synced
 * numbers (BirdStockView's per-pen mortality input vs. this module's
 * per-pen-per-category breakdown), so the backend preserves that rather than
 * unifying them, same reasoning as the crackGoodOpen/gcOpening precedent.
 * <p>
 * No carry-forward from the prior day (these are daily counts, not a stock
 * balance) — a new day's row starts at zero for every category, same as
 * {@code ProductionPenEntry}. Same-day edit lock is the entryDate == today
 * rule shared by every other module (see MortalityService).
 */
@Entity
@Table(name = "mort_pen_entries", uniqueConstraints = @UniqueConstraint(columnNames = {"pen_id", "entry_date"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MortPenEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pen_id", nullable = false)
    private Pen pen;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(nullable = false)
    @Builder.Default
    private int good = 0;

    @Column(nullable = false)
    @Builder.Default
    private int dry = 0;

    @Column(nullable = false)
    @Builder.Default
    private int runt = 0;

    @Column(nullable = false)
    @Builder.Default
    private int green = 0;

    @Column(name = "pm_reject", nullable = false)
    @Builder.Default
    private int pmReject = 0;

    @Column(name = "entered_by", length = 64)
    private String enteredBy;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    public int get(MortCat category) {
        return switch (category) {
            case GOOD -> good;
            case DRY -> dry;
            case RUNT -> runt;
            case GREEN -> green;
            case PM_REJECT -> pmReject;
        };
    }

    public void set(MortCat category, int value) {
        switch (category) {
            case GOOD -> good = value;
            case DRY -> dry = value;
            case RUNT -> runt = value;
            case GREEN -> green = value;
            case PM_REJECT -> pmReject = value;
        }
    }

    public Map<MortCat, Integer> asMap() {
        Map<MortCat, Integer> map = new EnumMap<>(MortCat.class);
        for (MortCat c : MortCat.values()) {
            map.put(c, get(c));
        }
        return map;
    }

    public int total() {
        return good + dry + runt + green + pmReject;
    }
}
