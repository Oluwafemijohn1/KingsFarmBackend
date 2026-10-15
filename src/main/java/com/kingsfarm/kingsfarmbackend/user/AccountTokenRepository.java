package com.kingsfarm.kingsfarmbackend.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface AccountTokenRepository extends JpaRepository<AccountToken, Long> {
    @Query("select t from AccountToken t join fetch t.user where t.tokenHash = :hash")
    Optional<AccountToken> findByTokenHash(String hash);

    @Modifying
    @Query("delete from AccountToken t where t.user = :user")
    void deleteAllForUser(User user);
}
