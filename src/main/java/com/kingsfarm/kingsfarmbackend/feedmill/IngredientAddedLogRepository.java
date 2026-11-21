package com.kingsfarm.kingsfarmbackend.feedmill;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IngredientAddedLogRepository extends JpaRepository<IngredientAddedLog, Long> {
}
