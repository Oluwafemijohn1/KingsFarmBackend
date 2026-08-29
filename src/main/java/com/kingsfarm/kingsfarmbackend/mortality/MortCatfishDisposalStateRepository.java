package com.kingsfarm.kingsfarmbackend.mortality;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MortCatfishDisposalStateRepository extends JpaRepository<MortCatfishDisposalState, Long> {
    Optional<MortCatfishDisposalState> findByEntryDate(LocalDate entryDate);

    /** Every day's row in a date range, inclusive — feeds the Reports daily/monthly endpoints (BACKEND_PLAN.md §8). */
    List<MortCatfishDisposalState> findAllByEntryDateBetween(LocalDate start, LocalDate end);
}
