package com.kingsfarm.kingsfarmbackend.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Signs the user out everywhere — used when their password is reset. */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update RefreshToken r set r.revokedAt = :now where r.user = :user and r.revokedAt is null")
    int revokeAllForUser(@org.springframework.data.repository.query.Param("user") User user, @org.springframework.data.repository.query.Param("now") java.time.Instant now);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from RefreshToken r where r.user.role <> :role")
    void deleteAllByUserRoleNot(@org.springframework.data.repository.query.Param("role") Role role);
}
