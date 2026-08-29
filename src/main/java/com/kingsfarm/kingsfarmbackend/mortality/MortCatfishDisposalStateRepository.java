package com.kingsfarm.kingsfarmbackend.mortality;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface MortCatfishDisposalStateRepository extends JpaRepository<MortCatfishDisposalState, Long> {
    Optional<MortCatfishDisposalState> findByEntryDate(LocalDate entryDate);
}
