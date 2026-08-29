package com.kingsfarm.kingsfarmbackend.feedmill;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeedFormulationHistoryItemRepository extends JpaRepository<FeedFormulationHistoryItem, Long> {
    List<FeedFormulationHistoryItem> findAllByGroupOrderByIdAsc(FeedFormulationHistoryGroup group);
}
