package com.kingsfarm.kingsfarmbackend.mortality;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface MortSaleEntryRepository extends JpaRepository<MortSaleEntry, Long> {
    Page<MortSaleEntry> findAllByOrderByOccurredAtDesc(Pageable pageable);

    /** Sales history with optional filters: bank ("" = any) and an instant range. */
    @Query(value = "select e from MortSaleEntry e where e.occurredAt >= :start and e.occurredAt < :end " +
            "and (:bank = '' or e.bank = :bank) order by e.occurredAt desc",
            countQuery = "select count(e) from MortSaleEntry e where e.occurredAt >= :start and e.occurredAt < :end " +
            "and (:bank = '' or e.bank = :bank)")
    Page<MortSaleEntry> findFiltered(@Param("bank") String bank, @Param("start") Instant start, @Param("end") Instant end, Pageable pageable);

    /** Same, but only sales paid (at least partly) with the given method, e.g. cash. */
    @Query(value = "select e from MortSaleEntry e where e.occurredAt >= :start and e.occurredAt < :end " +
            "and :method member of e.paymentMethods order by e.occurredAt desc",
            countQuery = "select count(e) from MortSaleEntry e where e.occurredAt >= :start and e.occurredAt < :end " +
            "and :method member of e.paymentMethods")
    Page<MortSaleEntry> findFilteredByMethod(@Param("method") com.kingsfarm.kingsfarmbackend.common.PaymentMethod method,
                                             @Param("start") Instant start, @Param("end") Instant end, Pageable pageable);

    @Query("select coalesce(sum(e.qty), 0) from MortSaleEntry e where e.category = :category")
    int sumQtyByCategory(@Param("category") MortCat category);

    @Query("select coalesce(sum(e.qty), 0) from MortSaleEntry e where e.category = :category and e.occurredAt >= :start and e.occurredAt < :end")
    int sumQtyByCategoryBetween(@Param("category") MortCat category, @Param("start") Instant start, @Param("end") Instant end);

    @Query("select coalesce(sum(e.qty * e.price), 0) from MortSaleEntry e")
    long sumRevenue();

    /** Every sale in a half-open instant range — feeds the Reports daily/monthly endpoints (BACKEND_PLAN.md §8). */
    List<MortSaleEntry> findAllByOccurredAtGreaterThanEqualAndOccurredAtLessThan(Instant start, Instant end);
}
