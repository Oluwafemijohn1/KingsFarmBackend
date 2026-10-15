package com.kingsfarm.kingsfarmbackend.systemlog;

import com.kingsfarm.kingsfarmbackend.common.Mod;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface SystemLogRepository extends JpaRepository<SystemLog, Long> {
    Page<SystemLog> findAllByLogTypeOrderByOccurredAtDesc(LogType logType, Pageable pageable);

    /** Module-scoped variant — backs a manager's own "My Recent Activity" dashboard panel (see DashboardService). */
    Page<SystemLog> findAllByLogTypeAndModuleInOrderByOccurredAtDesc(LogType logType, Collection<Mod> modules, Pageable pageable);
}
