package com.kingsfarm.kingsfarmbackend.feedmill;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedCollectionLogEntryRepository extends JpaRepository<FeedCollectionLogEntry, Long> {
    Page<FeedCollectionLogEntry> findAllByOrderByOccurredAtDesc(Pageable pageable);
}
