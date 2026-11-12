package com.kingsfarm.kingsfarmbackend.wholeegg;

import com.kingsfarm.kingsfarmbackend.common.CatKey;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A permanent line for every gift / sales-crack figure the Whole Egg manager saves: which day it belongs to,
 * which size, how many crates (negative when something was taken back), who entered it and when.
 * Stock itself is still moved by the existing day figures; this is the readable history.
 */
@Entity
@Table(name = "we_adjustment_log", indexes = @Index(name = "idx_we_adj_day", columnList = "day"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeAdjustmentLog {

    public enum Kind { GIFT, CRACK }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate day;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Kind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CatKey category;

    @Column(nullable = false)
    private double qty;

    @Column(name = "entered_by", length = 64)
    private String enteredBy;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @PrePersist
    void onCreate() {
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }
}
