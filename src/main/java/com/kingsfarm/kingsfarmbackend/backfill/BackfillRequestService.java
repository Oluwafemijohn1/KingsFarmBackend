package com.kingsfarm.kingsfarmbackend.backfill;

import com.kingsfarm.kingsfarmbackend.audit.Audited;
import com.kingsfarm.kingsfarmbackend.backfill.dto.BackfillRequestResponse;
import com.kingsfarm.kingsfarmbackend.backfill.dto.CreateBackfillRequestRequest;
import com.kingsfarm.kingsfarmbackend.backfill.dto.EditWindowResponse;
import com.kingsfarm.kingsfarmbackend.common.Mod;
import com.kingsfarm.kingsfarmbackend.common.exception.BadRequestException;
import com.kingsfarm.kingsfarmbackend.common.exception.NotFoundException;
import com.kingsfarm.kingsfarmbackend.openingstock.RequestStatus;
import com.kingsfarm.kingsfarmbackend.systemlog.LogType;
import com.kingsfarm.kingsfarmbackend.user.NotificationService;
import com.kingsfarm.kingsfarmbackend.user.Role;
import com.kingsfarm.kingsfarmbackend.user.User;
import com.kingsfarm.kingsfarmbackend.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class BackfillRequestService {

    /** How long an approved permission stays usable. */
    private static final long APPROVAL_HOURS = 24;

    private final BackfillRequestRepository repository;
    private final UserRepository userRepository;
    private final EditWindowService editWindow;
    private final DayClosureService dayClosures;
    private final NotificationService notifications;

    public BackfillRequestService(BackfillRequestRepository repository, UserRepository userRepository,
                                  EditWindowService editWindow, DayClosureService dayClosures, NotificationService notifications) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.editWindow = editWindow;
        this.dayClosures = dayClosures;
        this.notifications = notifications;
    }

    @Audited(module = Mod.ADMIN, action = "Backfill Permission Requested",
            detail = "#request.module() + ' / ' + #request.forDate() + ': ' + #request.reason()")
    @Transactional
    public BackfillRequestResponse create(CreateBackfillRequestRequest request, Long userId) {
        LocalDate today = LocalDate.now();
        if (!editWindow.isEnabled()) {
            throw new BadRequestException("Past-day entry is not switched on yet.");
        }
        if (request.forDate().isAfter(today)) {
            throw new BadRequestException("You can't request permission for a future date.");
        }
        if (editWindow.isGraceOpen(request.forDate())) {
            throw new BadRequestException("That day is still within the 24-hour grace period — no permission is needed.");
        }
        if (!dayClosures.isClosed(request.module(), request.forDate())) {
            LocalDate next = dayClosures.firstUnclosed(request.module());
            throw new BadRequestException("That day hasn't been closed yet, so no permission is needed — just enter days in order"
                    + (next != null ? " starting from " + next + "." : "."));
        }
        if (repository.existsByModuleAndForDateAndRequestedBy_IdAndStatus(request.module(), request.forDate(), userId, RequestStatus.PENDING)) {
            throw new BadRequestException("You already have a pending request for that day.");
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found."));
        BackfillRequest saved = repository.save(BackfillRequest.builder()
                .module(request.module()).forDate(request.forDate()).requestedBy(user)
                .reason(request.reason()).status(RequestStatus.PENDING).build());
        List<String> adminEmails = userRepository.findAllByRoleAndActiveTrue(Role.ADMINISTRATOR)
                .stream().map(User::getUsername).toList();
        notifications.backfillRequested(adminEmails, user.getFullName(), request.module().wire(), request.forDate().toString(), request.reason());
        return BackfillRequestResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Page<BackfillRequestResponse> list(RequestStatus status, Pageable pageable) {
        Page<BackfillRequest> page = status != null
                ? repository.findAllByStatusOrderByRequestedAtDesc(status, pageable)
                : repository.findAllByOrderByRequestedAtDesc(pageable);
        return page.map(BackfillRequestResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<BackfillRequestResponse> listMine(Long userId, Pageable pageable) {
        return repository.findAllByRequestedBy_IdOrderByRequestedAtDesc(userId, pageable).map(BackfillRequestResponse::from);
    }

    @Transactional(readOnly = true)
    public EditWindowResponse window(Mod module, LocalDate date, Long userId) {
        boolean grace = editWindow.isGraceOpen(date);
        boolean open = editWindow.isOpen(module, date);
        boolean pending = repository.existsByModuleAndForDateAndRequestedBy_IdAndStatus(module, date, userId, RequestStatus.PENDING);
        return new EditWindowResponse(open, grace, pending);
    }

    @Audited(module = Mod.ADMIN, action = "Backfill Permission Resolved", type = LogType.AUDIT,
            detail = "'Request #' + #requestId + ' -> ' + #decision")
    @Transactional
    public BackfillRequestResponse resolve(Long requestId, RequestStatus decision, Long adminId) {
        if (decision == RequestStatus.PENDING) {
            throw new BadRequestException("A request can only be resolved as APPROVED or DENIED.");
        }
        BackfillRequest request = repository.findById(requestId).orElseThrow(() -> new NotFoundException("Request not found."));
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new BadRequestException("This request has already been resolved.");
        }
        User admin = userRepository.findById(adminId).orElseThrow(() -> new NotFoundException("User not found."));
        request.setStatus(decision);
        request.setResolvedAt(Instant.now());
        request.setResolvedBy(admin);
        if (decision == RequestStatus.APPROVED) {
            request.setExpiresAt(Instant.now().plus(APPROVAL_HOURS, ChronoUnit.HOURS));
        }
        repository.save(request);
        User requester = request.getRequestedBy();
        notifications.backfillResolved(requester.getUsername(), requester.getFullName(), request.getModule().wire(),
                request.getForDate().toString(), decision == RequestStatus.APPROVED, admin.getFullName(), "/" + request.getModule().wire());
        return BackfillRequestResponse.from(request);
    }
}
