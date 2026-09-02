package com.kingsfarm.kingsfarmbackend.openingstock;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpeningStockRequestRepository extends JpaRepository<OpeningStockRequest, Long> {
    Page<OpeningStockRequest> findAllByOrderByRequestedAtDesc(Pageable pageable);
    Page<OpeningStockRequest> findAllByStatusOrderByRequestedAtDesc(RequestStatus status, Pageable pageable);

    // Backs LockStatusResponse.pendingRequestByMe — see its javadoc for why
    // this can't just reuse the (Administrator-only) list endpoint.
    boolean existsByModuleAndScopeAndRequestedBy_IdAndStatus(
            com.kingsfarm.kingsfarmbackend.common.Mod module, String scope, Long requestedByUserId, RequestStatus status);
}
