package com.kingsfarm.kingsfarmbackend.feedmill;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FeedFormulationEntryRepository extends JpaRepository<FeedFormulationEntry, Long> {
    List<FeedFormulationEntry> findAllByFeedType(FeedType feedType);
    Optional<FeedFormulationEntry> findByFeedTypeAndIngredient(FeedType feedType, FeedIngredient ingredient);
}
