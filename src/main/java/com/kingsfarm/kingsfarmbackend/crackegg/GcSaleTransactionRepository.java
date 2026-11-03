package com.kingsfarm.kingsfarmbackend.crackegg;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface GcSaleTransactionRepository extends JpaRepository<GcSaleTransaction, Long> {
    Page<GcSaleTransaction> findAllByOrderByOccurredAtDesc(Pageable pageable);
    List<GcSaleTransaction> findAllByOrderByOccurredAtDesc();

    @Query("select coalesce(sum(t.qty), 0) from GcSaleTransaction t")
    double sumQty();

    @Query("select coalesce(sum(t.qty), 0) from GcSaleTransaction t where t.occurredAt >= :start and t.occurredAt < :end")
    double sumQtyBetween(@org.springframework.data.repository.query.Param("start") Instant start, @org.springframework.data.repository.query.Param("end") Instant end);

    @Query("select coalesce(sum(t.qty * t.price), 0) from GcSaleTransaction t where t.occurredAt >= :start and t.occurredAt < :end")
    double sumRevenueBetween(@org.springframework.data.repository.query.Param("start") Instant start, @org.springframework.data.repository.query.Param("end") Instant end);

    @Query("select coalesce(sum(t.qty * t.price), 0) from GcSaleTransaction t")
    double sumRevenue();

    /** Every sale in a half-open instant range — feeds the Reports daily/monthly endpoints (BACKEND_PLAN.md §8). */
    List<GcSaleTransaction> findAllByOccurredAtGreaterThanEqualAndOccurredAtLessThan(Instant start, Instant end);
}
