package com.kingsfarm.kingsfarmbackend.wholeegg;

import com.kingsfarm.kingsfarmbackend.common.CatKey;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row per category actually sold on a transaction — sparse, unlike
 * Production's dense per-pen matrix, since most sales don't touch all six
 * categories (BACKEND_PLAN.md §5.3: "only categories actually sold get a
 * row"). A payment/opening transaction has none of these at all.
 */
@Entity
@Table(name = "we_sale_line_items", uniqueConstraints = @UniqueConstraint(columnNames = {"transaction_id", "category"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeSaleLineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private WeSaleTransaction transaction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private CatKey category;

    @Column(nullable = false)
    private int qty;

    /** Snapshot of the price at sale time — never re-derived from the live wePrices catalog, so past revenue never drifts if prices change later. */
    @Column(nullable = false)
    private long price;
}
