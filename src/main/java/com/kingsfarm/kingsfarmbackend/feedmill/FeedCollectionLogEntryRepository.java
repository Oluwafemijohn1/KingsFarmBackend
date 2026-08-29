package com.kingsfarm.kingsfarmbackend.feedmill;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface FeedCollectionLogEntryRepository extends JpaRepository<FeedCollectionLogEntry, Long> {
    Page<FeedCollectionLogEntry> findAllByOrderByOccurredAtDesc(Pageable pageable);

    /** Every collection in a half-open instant range — feeds the Reports daily/monthly endpoints (BACKEND_PLAN.md §8). */
    List<FeedCollectionLogEntry> findAllByOccurredAtGreaterThanEqualAndOccurredAtLessThan(Instant start, Instant end);
}
