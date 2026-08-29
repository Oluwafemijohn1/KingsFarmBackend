package com.kingsfarm.kingsfarmbackend.feedmill;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FeedIngredientRepository extends JpaRepository<FeedIngredient, Long> {
    List<FeedIngredient> findAllByOrderByNameAsc();
    Optional<FeedIngredient> findByName(String name);
    boolean existsByNameIgnoreCase(String name);
}
