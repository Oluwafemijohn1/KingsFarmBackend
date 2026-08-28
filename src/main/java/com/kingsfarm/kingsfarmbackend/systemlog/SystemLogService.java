package com.kingsfarm.kingsfarmbackend.systemlog;

import com.kingsfarm.kingsfarmbackend.common.Mod;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one place every part of the app writes a log row through — never
 * insert into system_logs directly, so the shape stays consistent. Logging
 * failures must never break the calling operation, so every write here is
 * best-effort from the caller's point of view (callers don't need to handle
 * exceptions from these methods beyond normal @Transactional propagation).
 */
@Service
public class SystemLogService {

    private final SystemLogRepository repository;

    public SystemLogService(SystemLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void logAccess(Long userId, String username, String action, String status, String ipAddress) {
        repository.save(SystemLog.builder()
                .logType(LogType.ACCESS)
                .userId(userId)
                .username(username)
                .action(action)
                .status(status)
                .ipAddress(ipAddress)
                .build());
    }

    @Transactional
    public void logActivity(Long userId, String username, Mod module, String action, String detail) {
        repository.save(SystemLog.builder()
                .logType(LogType.ACTIVITY)
                .userId(userId)
                .username(username)
                .module(module)
                .action(action)
                .detail(detail)
                .build());
    }

    @Transactional
    public void logAudit(Long userId, String username, Mod module, String action, String detail) {
        repository.save(SystemLog.builder()
                .logType(LogType.AUDIT)
                .userId(userId)
                .username(username)
                .module(module)
                .action(action)
                .detail(detail)
                .build());
    }

    @Transactional(readOnly = true)
    public Page<SystemLog> list(LogType logType, Pageable pageable) {
        return repository.findAllByLogTypeOrderByOccurredAtDesc(logType, pageable);
    }
}
