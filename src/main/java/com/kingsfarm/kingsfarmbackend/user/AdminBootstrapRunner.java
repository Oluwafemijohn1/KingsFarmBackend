package com.kingsfarm.kingsfarmbackend.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Solves the chicken-and-egg problem of "only an Administrator can create
 * users, but there are no users yet." Runs once on startup; if the users
 * table is empty, creates exactly one Administrator account with a
 * generated password logged to the console (and nowhere else — same
 * one-time-reveal contract as UserAdminService.createUser). On every
 * later startup this is a no-op.
 */
@Component
public class AdminBootstrapRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminFullName;

    public AdminBootstrapRunner(UserRepository userRepository,
                                 PasswordEncoder passwordEncoder,
                                 @Value("${app.bootstrap.admin-username}") String adminUsername,
                                 @Value("${app.bootstrap.admin-full-name}") String adminFullName) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminFullName = adminFullName;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }

        String generatedPassword = PasswordGenerator.generate();
        User admin = User.builder()
                .username(adminUsername)
                .fullName(adminFullName)
                .role(Role.ADMINISTRATOR)
                .passwordHash(passwordEncoder.encode(generatedPassword))
                .active(true)
                .mustChangePassword(true)
                .createdBy(null)
                .build();
        userRepository.save(admin);

        log.warn("=================================================================");
        log.warn(" No users found — created the initial Administrator account.");
        log.warn(" Username: {}", adminUsername);
        log.warn(" Password: {}", generatedPassword);
        log.warn(" This password is shown ONCE and is not recoverable — record it now.");
        log.warn("=================================================================");
    }
}
