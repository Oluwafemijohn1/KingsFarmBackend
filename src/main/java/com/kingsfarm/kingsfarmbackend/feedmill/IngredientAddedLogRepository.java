package com.kingsfarm.kingsfarmbackend.feedmill;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface IngredientAddedLogRepository extends JpaRepository<IngredientAddedLog, Long> {

    List<IngredientAddedLog> findAllByIngredientNameIgnoreCaseAndDay(String ingredientName, LocalDate day);
}
