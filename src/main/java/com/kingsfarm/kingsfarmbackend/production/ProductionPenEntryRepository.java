package com.kingsfarm.kingsfarmbackend.production;

import com.kingsfarm.kingsfarmbackend.birdstock.Pen;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ProductionPenEntryRepository extends JpaRepository<ProductionPenEntry, Long> {
    Optional<ProductionPenEntry> findByPenAndEntryDate(Pen pen, LocalDate entryDate);
    List<ProductionPenEntry> findAllByEntryDate(LocalDate entryDate);
    Page<ProductionPenEntry> findAllByPenOrderByEntryDateDesc(Pen pen, Pageable pageable);
    Page<ProductionPenEntry> findAllByOrderByEntryDateDesc(Pageable pageable);
}
