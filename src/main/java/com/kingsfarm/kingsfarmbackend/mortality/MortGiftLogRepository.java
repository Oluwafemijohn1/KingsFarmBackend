package com.kingsfarm.kingsfarmbackend.mortality;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface MortGiftLogRepository extends JpaRepository<MortGiftLogEntry, Long> {
    Page<MortGiftLogEntry> findAllByOrderByOccurredAtDesc(Pageable pageable);

    /** Every gift entry in a half-open instant range — feeds the Reports daily/monthly endpoints (BACKEND_PLAN.md §8). */
    List<MortGiftLogEntry> findAllByOccurredAtGreaterThanEqualAndOccurredAtLessThan(Instant start, Instant end);
}
