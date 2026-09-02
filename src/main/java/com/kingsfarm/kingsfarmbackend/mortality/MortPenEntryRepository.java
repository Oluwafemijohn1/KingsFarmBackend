package com.kingsfarm.kingsfarmbackend.mortality;

import com.kingsfarm.kingsfarmbackend.birdstock.Pen;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MortPenEntryRepository extends JpaRepository<MortPenEntry, Long> {
    Optional<MortPenEntry> findByPenAndEntryDate(Pen pen, LocalDate entryDate);
    List<MortPenEntry> findAllByEntryDate(LocalDate entryDate);
    Page<MortPenEntry> findAllByPenOrderByEntryDateDesc(Pen pen, Pageable pageable);

    /** Every pen's rows in a date range, inclusive — feeds the Reports daily/monthly endpoints (BACKEND_PLAN.md §8). */
    List<MortPenEntry> findAllByEntryDateBetween(LocalDate start, LocalDate end);
}
