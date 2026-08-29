package com.kingsfarm.kingsfarmbackend.feedmill;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FishFeedStockRepository extends JpaRepository<FishFeedStock, Long> {
    Optional<FishFeedStock> findByType(String type);
}
