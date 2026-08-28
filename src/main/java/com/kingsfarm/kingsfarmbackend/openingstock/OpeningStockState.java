package com.kingsfarm.kingsfarmbackend.openingstock;

import com.kingsfarm.kingsfarmbackend.common.Mod;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row per (module, scope) — e.g. ("bird-stock", "Pen A1") or
 * ("feed-mill", "Maize"). Absent row, or {@code unlocked = false}, means
 * locked: the field was carried forward from a prior close and can't be
 * hand-edited without an approved OpeningStockRequest. Generic across every
 * module instead of one-off per-module locking logic (BACKEND_PLAN.md §5.8).
 */
@Entity
@Table(name = "opening_stock_state", uniqueConstraints = @UniqueConstraint(columnNames = {"module", "scope"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OpeningStockState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Mod module;

    @Column(nullable = false, length = 128)
    private String scope;

    @Column(nullable = false)
    private boolean unlocked = false;
}
