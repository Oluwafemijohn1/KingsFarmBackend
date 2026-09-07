package com.kingsfarm.kingsfarmbackend.wholeegg;

import com.kingsfarm.kingsfarmbackend.common.CatKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface WeSaleLineItemRepository extends JpaRepository<WeSaleLineItem, Long> {

    List<WeSaleLineItem> findAllByTransaction(WeSaleTransaction transaction);

    void deleteAllByTransaction(WeSaleTransaction transaction);

    /** Per-category totals across every SALE-type transaction — feeds the Stock Overview's "Total Sales" column and Production's auto-received Total Sales. */
    @Query("select li.category as category, coalesce(sum(li.qty), 0) as qty from WeSaleLineItem li " +
            "where li.transaction.type = :type group by li.category")
    List<CategoryQtyProjection> sumQtyByCategoryAndType(@Param("type") WeSaleTxnType type);

    default List<CategoryQtyProjection> sumSoldQtyByCategory() {
        return sumQtyByCategoryAndType(WeSaleTxnType.SALE);
    }

    interface CategoryQtyProjection {
        CatKey getCategory();
        double getQty();
    }

    /** Every line item belonging to a SALE-type transaction in a half-open instant range — feeds the Reports daily/monthly endpoints (BACKEND_PLAN.md §8). */
    @Query("select li.transaction.occurredAt as occurredAt, li.qty as qty, li.price as price from WeSaleLineItem li " +
            "where li.transaction.type = :type and li.transaction.occurredAt >= :start and li.transaction.occurredAt < :end")
    List<SaleLineProjection> lineItemsInRange(@Param("type") WeSaleTxnType type, @Param("start") Instant start, @Param("end") Instant end);

    interface SaleLineProjection {
        Instant getOccurredAt();
        double getQty();
        long getPrice();
    }

    /**
     * Crates/revenue totals for one customer's SALE-type transactions, feeding
     * the Customers directory table (WholeEggView's customerStats). txnYear
     * null means "all time" (Administrator); a manager passes the current
     * year, matching the same restriction customerHistory/allTransactions
     * already apply. double: fractional crate sales must sum correctly
     * rather than being truncated (Crate Quantity & Conversion spec).
     */
    @Query("select coalesce(sum(li.qty), 0) from WeSaleLineItem li " +
            "where li.transaction.customer = :customer and li.transaction.type = :type " +
            "and (:txnYear is null or li.transaction.txnYear = :txnYear)")
    double sumQtyForCustomer(@Param("customer") Customer customer, @Param("type") WeSaleTxnType type, @Param("txnYear") Integer txnYear);

    @Query("select coalesce(sum(li.qty * li.price), 0) from WeSaleLineItem li " +
            "where li.transaction.customer = :customer and li.transaction.type = :type " +
            "and (:txnYear is null or li.transaction.txnYear = :txnYear)")
    double sumRevenueForCustomer(@Param("customer") Customer customer, @Param("type") WeSaleTxnType type, @Param("txnYear") Integer txnYear);
}
