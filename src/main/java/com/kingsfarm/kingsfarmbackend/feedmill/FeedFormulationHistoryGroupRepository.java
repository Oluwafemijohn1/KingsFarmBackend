package com.kingsfarm.kingsfarmbackend.feedmill;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedFormulationHistoryGroupRepository extends JpaRepository<FeedFormulationHistoryGroup, Long> {
    Page<FeedFormulationHistoryGroup> findAllByFeedTypeOrderByOccurredAtDesc(FeedType feedType, Pageable pageable);
}
