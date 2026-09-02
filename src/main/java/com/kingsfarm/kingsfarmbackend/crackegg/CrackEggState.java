package com.kingsfarm.kingsfarmbackend.crackegg;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Singleton row (id always 1) for every "current value" scalar on
 * CrackEggView's Stock Overview / Gift / Feed Mill tabs — gcOpening and
 * rcOpening are opening-stock-locked; the rest are freely editable and
 * update live the moment the manager types (no separate "commit" step),
 * exactly matching the frontend. Deliberately independent of
 * ProductionDayState's own crackGoodOpen/crackRoughOpen fields — the two
 * frontend screens read separate state today (never synced), and per the
 * instruction to keep existing behavior unchanged, this backend preserves
 * that rather than unifying them.
 */
@Entity
@Table(name = "crack_egg_state")
@Getter
@Setter
public class CrackEggState {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    @Column(name = "gc_opening", nullable = false)
    private int gcOpening = 240;

    @Column(name = "gc_selling_price", nullable = false)
    private long gcSellingPrice = 1800;

    @Column(name = "gc_gift_qty", nullable = false)
    private int gcGiftQty = 0;

    @Column(name = "gc_gift_recipient", length = 255)
    private String gcGiftRecipient = "";

    @Column(name = "gc_gift_authorizer", length = 255)
    private String gcGiftAuthorizer = "";

    @Column(name = "rc_opening", nullable = false)
    private int rcOpening = 180;

    @Column(name = "rc_feed_mill", nullable = false)
    private int rcFeedMill = 0;
}
