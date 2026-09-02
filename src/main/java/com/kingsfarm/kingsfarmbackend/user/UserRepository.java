package com.kingsfarm.kingsfarmbackend.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    Page<User> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Eligible pool for Relief Access — matches the frontend's eligibleUsers filter (non-Administrator, active accounts only). */
    List<User> findAllByRoleNotAndActiveTrueOrderByFullNameAsc(Role role);
}
