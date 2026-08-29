package com.kingsfarm.kingsfarmbackend.crackegg;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface GcSaleTransactionRepository extends JpaRepository<GcSaleTransaction, Long> {
    Page<GcSaleTransaction> findAllByOrderByOccurredAtDesc(Pageable pageable);
    List<GcSaleTransaction> findAllByOrderByOccurredAtDesc();

    @Query("select coalesce(sum(t.qty), 0) from GcSaleTransaction t")
    int sumQty();

    @Query("select coalesce(sum(t.qty * t.price), 0) from GcSaleTransaction t")
    long sumRevenue();
}
