package com.kingsfarm.kingsfarmbackend.feedmill;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface FeedProductionLogEntryRepository extends JpaRepository<FeedProductionLogEntry, Long> {
    Page<FeedProductionLogEntry> findAllByOrderByOccurredAtDesc(Pageable pageable);
    List<FeedProductionLogEntry> findAllByFeedType(FeedType feedType);

    /** Every production run in a half-open instant range — feeds the Reports daily/monthly endpoints (BACKEND_PLAN.md §8). */
    List<FeedProductionLogEntry> findAllByOccurredAtGreaterThanEqualAndOccurredAtLessThan(Instant start, Instant end);
}
