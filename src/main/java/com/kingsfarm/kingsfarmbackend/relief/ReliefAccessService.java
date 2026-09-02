package com.kingsfarm.kingsfarmbackend.relief;

import com.kingsfarm.kingsfarmbackend.audit.Audited;
import com.kingsfarm.kingsfarmbackend.common.Mod;
import com.kingsfarm.kingsfarmbackend.common.exception.BadRequestException;
import com.kingsfarm.kingsfarmbackend.common.exception.ConflictException;
import com.kingsfarm.kingsfarmbackend.common.exception.NotFoundException;
import com.kingsfarm.kingsfarmbackend.relief.dto.CreateReliefGrantRequest;
import com.kingsfarm.kingsfarmbackend.systemlog.LogType;
import com.kingsfarm.kingsfarmbackend.user.Role;
import com.kingsfarm.kingsfarmbackend.user.User;
import com.kingsfarm.kingsfarmbackend.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Backs AdminView's Relief Access tab — Administrator-only to grant/revoke
 * (see ReliefAccessController). See {@link ReliefGrant}'s javadoc for the
 * full mechanics of how an active grant reaches into login and JWT
 * authorization.
 */
@Service
public class ReliefAccessService {

    private final ReliefGrantRepository grantRepository;
    private final UserRepository userRepository;

    public ReliefAccessService(ReliefGrantRepository grantRepository, UserRepository userRepository) {
        this.grantRepository = grantRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<User> eligibleUsers() {
        return userRepository.findAllByRoleNotAndActiveTrueOrderByFullNameAsc(Role.ADMINISTRATOR);
    }

    @Transactional(readOnly = true)
    public List<ReliefGrant> activeGrants() {
        return grantRepository.findAllByActiveTrueOrderByGrantedAtDesc();
    }

    @Transactional(readOnly = true)
    public Page<ReliefGrant> pastGrants(Pageable pageable) {
        return grantRepository.findAllByActiveFalseOrderByGrantedAtDesc(pageable);
    }

    /**
     * Validation mirrors {@code submitGrant} in ReliefAccessView.tsx exactly:
     * the relieving officer can't cover for themselves, and the person going
     * on leave can't already have an active grant (the frontend surfaces the
     * existing grantee's name in that error — replicated here).
     */
    @Audited(module = Mod.ADMIN, action = "Grant Relief Access", type = LogType.AUDIT,
            detail = "'On leave: ' + #request.onLeaveUserId() + ', Relieving: ' + #request.granteeUserId()")
    @Transactional
    public ReliefGrant grant(CreateReliefGrantRequest request, String adminUsername) {
        if (request.onLeaveUserId().equals(request.granteeUserId())) {
            throw new BadRequestException("The relieving officer can't cover for themselves.");
        }
        User onLeave = userRepository.findById(request.onLeaveUserId())
                .orElseThrow(() -> new NotFoundException("Staff member (on leave) not found."));
        User grantee = userRepository.findById(request.granteeUserId())
                .orElseThrow(() -> new NotFoundException("Staff member (relieving officer) not found."));
        if (onLeave.getRole() == Role.ADMINISTRATOR || grantee.getRole() == Role.ADMINISTRATOR) {
            throw new BadRequestException("Administrator accounts can't be part of a relief grant.");
        }

        grantRepository.findByOnLeaveUserAndActiveTrue(onLeave).ifPresent(existing -> {
            throw new ConflictException(onLeave.getFullName() + " is already marked on leave, covered by "
                    + existing.getGranteeUser().getFullName() + ". Revoke that grant first.");
        });

        ReliefGrant grantEntity = ReliefGrant.builder()
                .onLeaveUser(onLeave).granteeUser(grantee)
                .reason(request.reason() == null ? "" : request.reason().trim())
                .grantedBy(adminUsername)
                .build();
        return grantRepository.save(grantEntity);
    }

    @Audited(module = Mod.ADMIN, action = "Revoke Relief Access", type = LogType.AUDIT, detail = "'Grant #' + #id")
    @Transactional
    public ReliefGrant revoke(Long id, String adminUsername) {
        ReliefGrant grant = grantRepository.findById(id).orElseThrow(() -> new NotFoundException("Relief grant not found."));
        if (!grant.isActive()) {
            return grant;
        }
        grant.setActive(false);
        grant.setRevokedAt(Instant.now());
        grant.setRevokedBy(adminUsername);
        return grantRepository.save(grant);
    }

    /**
     * Called from UserAdminService.setActive when a user is deactivated —
     * revokes every active grant naming them on either side, matching the
     * frontend's setAccountActive comment ("clears any relief grants tied to
     * that username, either side, so the access picture stays consistent").
     * Deliberately not {@code @Audited} itself — the deactivation that
     * triggered this already produces its own audit row; this is a
     * side-effect of that action, not a separate admin decision.
     */
    @Transactional
    public void revokeAllForUser(User user, String actingUsername) {
        Instant now = Instant.now();
        for (ReliefGrant grant : grantRepository.findAllByOnLeaveUserAndActiveTrue(user)) {
            grant.setActive(false);
            grant.setRevokedAt(now);
            grant.setRevokedBy(actingUsername);
            grantRepository.save(grant);
        }
        for (ReliefGrant grant : grantRepository.findAllByGranteeUserAndActiveTrue(user)) {
            grant.setActive(false);
            grant.setRevokedAt(now);
            grant.setRevokedBy(actingUsername);
            grantRepository.save(grant);
        }
    }

    /** Roles this user currently covers via an active grant as the relieving officer — feeds AuthService's extra-authority computation at login/refresh time. */
    @Transactional(readOnly = true)
    public List<Role> extraRolesFor(User grantee) {
        return grantRepository.findAllByGranteeUserAndActiveTrue(grantee).stream()
                .map(g -> g.getOnLeaveUser().getRole())
                .distinct()
                .toList();
    }

    /** True while this user is the on-leave party of an active grant — feeds AuthService.login's hard login block. */
    @Transactional(readOnly = true)
    public ReliefGrant activeLeaveFor(User user) {
        return grantRepository.findByOnLeaveUserAndActiveTrue(user).orElse(null);
    }
}
