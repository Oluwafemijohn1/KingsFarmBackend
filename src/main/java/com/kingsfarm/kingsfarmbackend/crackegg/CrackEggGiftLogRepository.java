package com.kingsfarm.kingsfarmbackend.crackegg;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface CrackEggGiftLogRepository extends JpaRepository<CrackEggGiftLogEntry, Long> {
    Page<CrackEggGiftLogEntry> findAllByOrderByOccurredAtDesc(Pageable pageable);

    /** Every gift entry in a half-open instant range — feeds the Reports daily/monthly endpoints (BACKEND_PLAN.md §8). */
    List<CrackEggGiftLogEntry> findAllByOccurredAtGreaterThanEqualAndOccurredAtLessThan(Instant start, Instant end);
}
