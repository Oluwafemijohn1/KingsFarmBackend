package com.kingsfarm.kingsfarmbackend.feedmill;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface FeedProductionLogEntryRepository extends JpaRepository<FeedProductionLogEntry, Long> {
    Page<FeedProductionLogEntry> findAllByOrderByOccurredAtDesc(Pageable pageable);
    List<FeedProductionLogEntry> findAllByFeedType(FeedType feedType);

    /**
     * Backs the Fish Feed Production History view — true server-side
     * pagination filtered to just the fish feed types (case-insensitively,
     * so a FeedType saved as e.g. "Fish starter" still matches "Fish
     * Starter"), rather than fetching a fixed window and paging/filtering
     * client-side. An explicit JPQL query, not a derived
     * findBy...IgnoreCaseIn... method name — that keyword combination
     * (IgnoreCase + In together) caused a runtime 500 rather than the
     * intended case-insensitive match, so this spells the LOWER() compare
     * out directly. Caller passes already-lowercased names.
     */
    @Query("SELECT p FROM FeedProductionLogEntry p WHERE LOWER(p.feedType.name) IN :lowerNames ORDER BY p.occurredAt DESC")
    Page<FeedProductionLogEntry> findAllByFeedTypeNameLowerIn(@Param("lowerNames") Collection<String> lowerNames, Pageable pageable);

    /** Every production run in a half-open instant range — feeds the Reports daily/monthly endpoints (BACKEND_PLAN.md §8). */
    List<FeedProductionLogEntry> findAllByOccurredAtGreaterThanEqualAndOccurredAtLessThan(Instant start, Instant end);
}
