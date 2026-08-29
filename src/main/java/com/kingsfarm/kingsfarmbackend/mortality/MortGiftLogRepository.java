package com.kingsfarm.kingsfarmbackend.mortality;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MortGiftLogRepository extends JpaRepository<MortGiftLogEntry, Long> {
    Page<MortGiftLogEntry> findAllByOrderByOccurredAtDesc(Pageable pageable);
}
