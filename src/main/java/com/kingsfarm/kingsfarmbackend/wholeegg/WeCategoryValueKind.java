package com.kingsfarm.kingsfarmbackend.wholeegg;

/**
 * Discriminator for the one small "current value per category" table
 * (WeCategoryValue) backing weOpening/wePrices/weSalesCrack/weGift — these
 * are running totals the frontend keeps as always-current single values
 * (no day dimension), not per-record logs. See BACKEND_PLAN.md §5.3: "these
 * are running totals... matching frontend behavior exactly."
 */
public enum WeCategoryValueKind {
    /** Opening stock per category — opening-stock-locked via OpeningStockLockService. */
    OPENING,
    /** Current selling price per category — freely editable, never locked. */
    PRICE,
    /** Committed "Sales Crack" qty per category, transferred to Production's Crack Use. */
    SALES_CRACK,
    /** Committed Gift qty per category, transferred to Production's Gift. */
    GIFT
}
