package com.kingsfarm.kingsfarmbackend.openingstock;

import com.kingsfarm.kingsfarmbackend.audit.Audited;
import com.kingsfarm.kingsfarmbackend.common.exception.BadRequestException;
import com.kingsfarm.kingsfarmbackend.common.exception.NotFoundException;
import com.kingsfarm.kingsfarmbackend.openingstock.dto.CreateOpeningStockRequestRequest;
import com.kingsfarm.kingsfarmbackend.systemlog.LogType;
import com.kingsfarm.kingsfarmbackend.user.User;
import com.kingsfarm.kingsfarmbackend.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class OpeningStockRequestService {

    private final OpeningStockRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final OpeningStockLockService lockService;

    public OpeningStockRequestService(OpeningStockRequestRepository requestRepository,
                                       UserRepository userRepository,
                                       OpeningStockLockService lockService) {
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.lockService = lockService;
    }

    @Audited(module = com.kingsfarm.kingsfarmbackend.common.Mod.ADMIN, action = "Opening Stock Unlock Requested",
            detail = "#request.module() + ' / ' + #request.scopeLabel() + ': ' + #request.reason()")
    @Transactional
    public OpeningStockRequest create(CreateOpeningStockRequestRequest request, Long requestedByUserId) {
        User requestedBy = userRepository.findById(requestedByUserId)
                .orElseThrow(() -> new NotFoundException("User not found."));
        OpeningStockRequest entity = OpeningStockRequest.builder()
                .module(request.module())
                .scope(request.scope())
                .scopeLabel(request.scopeLabel())
                .requestedBy(requestedBy)
                .reason(request.reason())
                .status(RequestStatus.PENDING)
                .build();
        return requestRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public Page<OpeningStockRequest> list(RequestStatus statusFilter, Pageable pageable) {
        return statusFilter != null
                ? requestRepository.findAllByStatusOrderByRequestedAtDesc(statusFilter, pageable)
                : requestRepository.findAllByOrderByRequestedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public boolean hasPendingRequestByUser(com.kingsfarm.kingsfarmbackend.common.Mod module, String scope, Long userId) {
        return requestRepository.existsByModuleAndScopeAndRequestedBy_IdAndStatus(module, scope, userId, RequestStatus.PENDING);
    }

    @Audited(module = com.kingsfarm.kingsfarmbackend.common.Mod.ADMIN, action = "Opening Stock Unlock Resolved", type = LogType.AUDIT,
            detail = "'Request #' + #requestId + ' -> ' + #decision")
    @Transactional
    public OpeningStockRequest resolve(Long requestId, RequestStatus decision, Long resolvedByUserId) {
        if (decision == RequestStatus.PENDING) {
            throw new BadRequestException("A request can only be resolved as APPROVED or DENIED.");
        }
        OpeningStockRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Opening stock request not found."));
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new BadRequestException("This request has already been resolved.");
        }
        User resolvedBy = userRepository.findById(resolvedByUserId)
                .orElseThrow(() -> new NotFoundException("User not found."));

        request.setStatus(decision);
        request.setResolvedAt(Instant.now());
        request.setResolvedBy(resolvedBy);
        requestRepository.save(request);

        if (decision == RequestStatus.APPROVED) {
            lockService.unlock(request.getModule(), request.getScope());
        }
        return request;
    }
}
