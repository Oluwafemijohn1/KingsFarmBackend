package com.kingsfarm.kingsfarmbackend.relief;

import com.kingsfarm.kingsfarmbackend.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReliefGrantRepository extends JpaRepository<ReliefGrant, Long> {
    List<ReliefGrant> findAllByActiveTrueOrderByGrantedAtDesc();
    Page<ReliefGrant> findAllByActiveFalseOrderByGrantedAtDesc(Pageable pageable);
    Optional<ReliefGrant> findByOnLeaveUserAndActiveTrue(User onLeaveUser);
    List<ReliefGrant> findAllByGranteeUserAndActiveTrue(User granteeUser);
    List<ReliefGrant> findAllByOnLeaveUserAndActiveTrue(User onLeaveUser);
}
