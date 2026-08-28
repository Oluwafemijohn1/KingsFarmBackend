package com.kingsfarm.kingsfarmbackend.user;

import com.kingsfarm.kingsfarmbackend.common.exception.ConflictException;
import com.kingsfarm.kingsfarmbackend.common.exception.NotFoundException;
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

    public UserAdminService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public CreateUserResponse createUser(CreateUserRequest request, String createdByUsername) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ConflictException("That username is already taken.");
        }
        String generatedPassword = PasswordGenerator.generate();
        User user = User.builder()
                .username(request.username())
                .fullName(request.fullName())
                .role(request.role())
                .passwordHash(passwordEncoder.encode(generatedPassword))
                .active(true)
                .mustChangePassword(true)
                .createdBy(createdByUsername)
                .build();
        user = userRepository.save(user);
        return new CreateUserResponse(user.getId(), user.getUsername(), user.getFullName(), user.getRole(), user.getRole().label(), generatedPassword);
    }

    @Transactional(readOnly = true)
    public Page<UserSummaryResponse> listUsers(Pageable pageable) {
        return userRepository.findAllByOrderByCreatedAtDesc(pageable).map(UserSummaryResponse::from);
    }

    @Transactional
    public UserSummaryResponse updateUser(Long id, UpdateUserRequest request) {
        User user = findOrThrow(id);
        user.setFullName(request.fullName());
        user.setRole(request.role());
        return UserSummaryResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserSummaryResponse setActive(Long id, boolean active) {
        User user = findOrThrow(id);
        user.setActive(active);
        return UserSummaryResponse.from(userRepository.save(user));
    }

    @Transactional
    public ResetPasswordResponse resetPassword(Long id) {
        User user = findOrThrow(id);
        String generatedPassword = PasswordGenerator.generate();
        user.setPasswordHash(passwordEncoder.encode(generatedPassword));
        user.setMustChangePassword(true);
        userRepository.save(user);
        return new ResetPasswordResponse(user.getId(), user.getUsername(), generatedPassword);
    }

    private User findOrThrow(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new NotFoundException("User not found."));
    }
}
