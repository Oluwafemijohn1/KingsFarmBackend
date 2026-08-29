package com.kingsfarm.kingsfarmbackend.mortality;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MortSaleEntryRepository extends JpaRepository<MortSaleEntry, Long> {
    Page<MortSaleEntry> findAllByOrderByOccurredAtDesc(Pageable pageable);

    @Query("select coalesce(sum(e.qty), 0) from MortSaleEntry e where e.category = :category")
    int sumQtyByCategory(@Param("category") MortCat category);

    @Query("select coalesce(sum(e.qty * e.price), 0) from MortSaleEntry e")
    long sumRevenue();
}
