package com.kingsfarm.kingsfarmbackend.backfill;

import com.kingsfarm.kingsfarmbackend.common.Mod;
import com.kingsfarm.kingsfarmbackend.openingstock.RequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.time.LocalDate;

public interface BackfillRequestRepository extends JpaRepository<BackfillRequest, Long> {
    Page<BackfillRequest> findAllByOrderByRequestedAtDesc(Pageable pageable);
    Page<BackfillRequest> findAllByStatusOrderByRequestedAtDesc(RequestStatus status, Pageable pageable);
    Page<BackfillRequest> findAllByRequestedBy_IdOrderByRequestedAtDesc(Long userId, Pageable pageable);

    boolean existsByModuleAndForDateAndRequestedBy_IdAndStatus(Mod module, LocalDate forDate, Long userId, RequestStatus status);

    boolean existsByModuleAndForDateAndRequestedBy_IdAndStatusAndExpiresAtAfter(
            Mod module, LocalDate forDate, Long userId, RequestStatus status, Instant now);
}
