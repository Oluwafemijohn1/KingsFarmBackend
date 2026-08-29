package com.kingsfarm.kingsfarmbackend.crackegg;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrackEggGiftLogRepository extends JpaRepository<CrackEggGiftLogEntry, Long> {
    Page<CrackEggGiftLogEntry> findAllByOrderByOccurredAtDesc(Pageable pageable);
}
