package com.kingsfarm.kingsfarmbackend.production;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface ProductionDayStateRepository extends JpaRepository<ProductionDayState, Long> {
    Optional<ProductionDayState> findByEntryDate(LocalDate entryDate);
    Optional<ProductionDayState> findFirstByEntryDateLessThanOrderByEntryDateDesc(LocalDate entryDate);
    Page<ProductionDayState> findAllByOrderByEntryDateDesc(Pageable pageable);
}
