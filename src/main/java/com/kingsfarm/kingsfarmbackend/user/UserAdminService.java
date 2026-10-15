package com.kingsfarm.kingsfarmbackend.user;

import com.kingsfarm.kingsfarmbackend.audit.Audited;
import com.kingsfarm.kingsfarmbackend.common.Mod;
import com.kingsfarm.kingsfarmbackend.common.exception.ConflictException;
import com.kingsfarm.kingsfarmbackend.common.exception.NotFoundException;
import com.kingsfarm.kingsfarmbackend.relief.ReliefAccessService;
import com.kingsfarm.kingsfarmbackend.systemlog.LogType;
import com.kingsfarm.kingsfarmbackend.user.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Backs the Admin → Users tab. Every account here is created by an
 * Administrator — there is no self-registration endpoint anywhere in the
 * API, matching the "internal application only" requirement.
 */
@Service
public class UserAdminService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ReliefAccessService reliefAccessService;
    private final AccountSetupService accountSetupService;

    public UserAdminService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                            ReliefAccessService reliefAccessService, AccountSetupService accountSetupService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.reliefAccessService = reliefAccessService;
        this.accountSetupService = accountSetupService;
    }

    @Audited(module = Mod.ADMIN, action = "Create User", type = LogType.AUDIT,
            detail = "'New user: ' + #request.username() + ' (' + #request.role() + ')'")
    @Transactional
    public CreateUserResponse createUser(CreateUserRequest request, String createdByUsername) {
        String email = request.username().trim().toLowerCase();
        if (userRepository.existsByUsername(email)) {
            throw new ConflictException("A user with that email already exists.");
        }
        String generatedPassword = PasswordGenerator.generate();
        User user = User.builder()
                .username(email)
                .fullName(request.fullName())
                .role(request.role())
                .passwordHash(passwordEncoder.encode(generatedPassword))
                .active(true)
                .mustChangePassword(true)
                .createdBy(createdByUsername)
                .build();
        user = userRepository.save(user);
        boolean sent = accountSetupService.issueAndSend(user, false);
        return new CreateUserResponse(user.getId(), user.getUsername(), user.getFullName(), user.getRole(), user.getRole().label(), sent);
    }

    @Transactional(readOnly = true)
    public Page<UserSummaryResponse> listUsers(Pageable pageable) {
        return userRepository.findAllByOrderByCreatedAtDesc(pageable).map(UserSummaryResponse::from);
    }

    @Audited(module = Mod.ADMIN, action = "Update User", type = LogType.AUDIT,
            detail = "'Updated user #' + #id + ' -> ' + #request.fullName() + ' (' + #request.role() + ')'")
    @Transactional
    public UserSummaryResponse updateUser(Long id, UpdateUserRequest request) {
        User user = findOrThrow(id);
        user.setFullName(request.fullName());
        user.setRole(request.role());
        return UserSummaryResponse.from(userRepository.save(user));
    }

    /**
     * Deactivating a user also revokes every active Relief Access grant tied
     * to them, on either side — matches the frontend's setAccountActive
     * comment ("clears any relief grants tied to that username, either
     * side, so the access picture stays consistent"). Reactivating never
     * restores a grant; the Administrator would need to grant it again.
     */
    @Audited(module = Mod.ADMIN, action = "Set User Active", type = LogType.AUDIT,
            detail = "'User #' + #id + ' -> ' + (#active ? 'Activated' : 'Deactivated')")
    @Transactional
    public UserSummaryResponse setActive(Long id, boolean active, String actingUsername) {
        User user = findOrThrow(id);
        user.setActive(active);
        User saved = userRepository.save(user);
        if (!active) {
            reliefAccessService.revokeAllForUser(saved, actingUsername);
        }
        return UserSummaryResponse.from(saved);
    }

    @Audited(module = Mod.ADMIN, action = "Reset Password", type = LogType.AUDIT,
            detail = "'Password reset for user #' + #id")
    @Transactional
    public ResetPasswordResponse resetPassword(Long id) {
        User user = findOrThrow(id);
        String generatedPassword = PasswordGenerator.generate();
        user.setPasswordHash(passwordEncoder.encode(generatedPassword));
        user.setMustChangePassword(true);
        userRepository.save(user);
        boolean sent = accountSetupService.issueAndSend(user, true);
        return new ResetPasswordResponse(user.getId(), user.getUsername(), sent);
    }

    /** Re-sends the verification / set-password email (new link; earlier links stop working). */
    @Audited(module = Mod.ADMIN, action = "Resend Invitation", type = LogType.AUDIT,
            detail = "'Invitation resent for user #' + #id")
    @Transactional
    public ResetPasswordResponse resendInvitation(Long id) {
        User user = findOrThrow(id);
        boolean sent = accountSetupService.issueAndSend(user, user.isEmailVerified());
        return new ResetPasswordResponse(user.getId(), user.getUsername(), sent);
    }

    private User findOrThrow(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new NotFoundException("User not found."));
    }
}
