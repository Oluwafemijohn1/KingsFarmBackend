package com.kingsfarm.kingsfarmbackend.systemlog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemLogRepository extends JpaRepository<SystemLog, Long> {
    Page<SystemLog> findAllByLogTypeOrderByOccurredAtDesc(LogType logType, Pageable pageable);
}
