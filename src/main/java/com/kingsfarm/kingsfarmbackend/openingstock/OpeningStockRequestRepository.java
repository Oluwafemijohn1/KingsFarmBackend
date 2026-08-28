package com.kingsfarm.kingsfarmbackend.openingstock;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpeningStockRequestRepository extends JpaRepository<OpeningStockRequest, Long> {
    Page<OpeningStockRequest> findAllByOrderByRequestedAtDesc(Pageable pageable);
    Page<OpeningStockRequest> findAllByStatusOrderByRequestedAtDesc(RequestStatus status, Pageable pageable);
}
