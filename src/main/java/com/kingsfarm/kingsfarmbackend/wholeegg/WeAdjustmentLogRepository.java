package com.kingsfarm.kingsfarmbackend.wholeegg;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface WeAdjustmentLogRepository extends JpaRepository<WeAdjustmentLog, Long> {

    boolean existsByEnteredBy(String enteredBy);

    List<WeAdjustmentLog> findAllByDayBetweenOrderByDayDescOccurredAtDesc(LocalDate from, LocalDate to);
}
