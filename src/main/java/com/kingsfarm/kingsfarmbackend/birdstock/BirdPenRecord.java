package com.kingsfarm.kingsfarmbackend.birdstock;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * One row per (pen, day). Opening is normally carried forward automatically
 * from the prior day's closing (see BirdStockService) and locked via
 * OpeningStockLockService the moment that happens — a manager can only ever
 * hand-type it once, or after an approved OpeningStockRequest. Closing is
 * never stored — it's always {@code opening - mortality - birdSales +
 * restocking}, computed at read time (see BirdPenRecordResponse) so there's
 * exactly one source of truth for it.
 */
@Entity
@Table(name = "bird_pen_records", uniqueConstraints = @UniqueConstraint(columnNames = {"pen_id", "entry_date"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BirdPenRecord {

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
    private int opening = 0;

    @Column(nullable = false)
    @Builder.Default
    private int mortality = 0;

    @Column(name = "bird_sales", nullable = false)
    @Builder.Default
    private int birdSales = 0;

    @Column(nullable = false)
    @Builder.Default
    private int restocking = 0;

    @Column(length = 500)
    private String remarks;

    @Column(name = "entered_by", length = 64)
    private String enteredBy;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    public int closing() {
        return opening - mortality - birdSales + restocking;
    }
}
