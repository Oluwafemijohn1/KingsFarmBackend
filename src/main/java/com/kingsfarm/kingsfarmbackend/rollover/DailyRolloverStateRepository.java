package com.kingsfarm.kingsfarmbackend.rollover;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyRolloverStateRepository extends JpaRepository<DailyRolloverState, Long> {
}
