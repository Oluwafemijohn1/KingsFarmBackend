package com.kingsfarm.kingsfarmbackend.birdstock;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BirdPenRecordRepository extends JpaRepository<BirdPenRecord, Long> {
    Optional<BirdPenRecord> findByPenAndEntryDate(Pen pen, LocalDate entryDate);
    List<BirdPenRecord> findAllByEntryDate(LocalDate entryDate);
    Page<BirdPenRecord> findAllByPenOrderByEntryDateDesc(Pen pen, Pageable pageable);
    Page<BirdPenRecord> findAllByOrderByEntryDateDesc(Pageable pageable);

    /** Most recent record strictly before the given date — how a new day's opening is carried forward. */
    Optional<BirdPenRecord> findFirstByPenAndEntryDateLessThanOrderByEntryDateDesc(Pen pen, LocalDate entryDate);

    /** Every pen's rows in a date range, inclusive — feeds the Reports daily/monthly endpoints (BACKEND_PLAN.md §8). */
    List<BirdPenRecord> findAllByEntryDateBetween(LocalDate start, LocalDate end);
}
