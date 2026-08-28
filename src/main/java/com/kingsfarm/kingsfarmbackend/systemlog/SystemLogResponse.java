package com.kingsfarm.kingsfarmbackend.systemlog;

import com.kingsfarm.kingsfarmbackend.common.Mod;

import java.time.Instant;

public record SystemLogResponse(
        Long id,
        LogType logType,
        Instant occurredAt,
        String username,
        Mod module,
        String action,
        String detail,
        String status,
        String ipAddress
) {
    public static SystemLogResponse from(SystemLog log) {
        return new SystemLogResponse(
                log.getId(), log.getLogType(), log.getOccurredAt(), log.getUsername(),
                log.getModule(), log.getAction(), log.getDetail(), log.getStatus(), log.getIpAddress()
        );
    }
}
