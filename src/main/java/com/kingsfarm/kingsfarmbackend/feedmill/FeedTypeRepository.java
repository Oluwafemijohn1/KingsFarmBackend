package com.kingsfarm.kingsfarmbackend.feedmill;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FeedTypeRepository extends JpaRepository<FeedType, Long> {
    List<FeedType> findAllByOrderByIdAsc();
    Optional<FeedType> findByName(String name);
    boolean existsByNameIgnoreCase(String name);
}
