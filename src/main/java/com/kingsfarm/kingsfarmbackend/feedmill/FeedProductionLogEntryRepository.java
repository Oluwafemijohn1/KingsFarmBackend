package com.kingsfarm.kingsfarmbackend.feedmill;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeedProductionLogEntryRepository extends JpaRepository<FeedProductionLogEntry, Long> {
    Page<FeedProductionLogEntry> findAllByOrderByOccurredAtDesc(Pageable pageable);
    List<FeedProductionLogEntry> findAllByFeedType(FeedType feedType);
}
