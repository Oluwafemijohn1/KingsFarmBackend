package com.kingsfarm.kingsfarmbackend.dashboard;

import com.kingsfarm.kingsfarmbackend.common.Mod;
import com.kingsfarm.kingsfarmbackend.systemlog.LogType;
import com.kingsfarm.kingsfarmbackend.systemlog.SystemLogRepository;
import com.kingsfarm.kingsfarmbackend.systemlog.SystemLogResponse;
import com.kingsfarm.kingsfarmbackend.user.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Backs DashboardView's "Recent Farm Activity" (Administrator/Managing
 * Director — farm-wide) and "My Recent Activity" (every manager role —
 * scoped to their own module(s) only) panels with real {@link
 * com.kingsfarm.kingsfarmbackend.systemlog.SystemLog} AUDIT rows, replacing
 * DashboardView.tsx's old hardcoded AUDIT array. The module scope is derived
 * server-side from the caller's own role — never trusted from the client —
 * mirroring the old frontend's moduleAuditFilter mapping exactly (a
 * Production Manager sees Bird Stock + Production activity, since Bird Stock
 * feeds that manager's own dashboard section too).
 */
@Service
public class DashboardService {

    private static final Map<Role, List<Mod>> MODULE_FILTER = new EnumMap<>(Role.class);

    static {
        MODULE_FILTER.put(Role.PRODUCTION_MANAGER, List.of(Mod.BIRD_STOCK, Mod.PRODUCTION));
        MODULE_FILTER.put(Role.WHOLE_EGG_MANAGER, List.of(Mod.WHOLE_EGG));
        MODULE_FILTER.put(Role.CRACK_EGG_MANAGER, List.of(Mod.CRACK_EGG));
        MODULE_FILTER.put(Role.FEED_MILL_MANAGER, List.of(Mod.FEED_MILL));
        MODULE_FILTER.put(Role.MORTALITY_MANAGER, List.of(Mod.MORTALITY));
        // ADMINISTRATOR / MANAGING_DIRECTOR intentionally absent — no filter, farm-wide.
    }

    private final SystemLogRepository systemLogRepository;

    public DashboardService(SystemLogRepository systemLogRepository) {
        this.systemLogRepository = systemLogRepository;
    }

    @Transactional(readOnly = true)
    public List<SystemLogResponse> recentActivity(Role role, int limit) {
        List<Mod> modules = MODULE_FILTER.get(role);
        PageRequest page = PageRequest.of(0, Math.max(1, limit));
        Page<com.kingsfarm.kingsfarmbackend.systemlog.SystemLog> result = modules == null
                ? systemLogRepository.findAllByLogTypeOrderByOccurredAtDesc(LogType.AUDIT, page)
                : systemLogRepository.findAllByLogTypeAndModuleInOrderByOccurredAtDesc(LogType.AUDIT, modules, page);
        return result.map(SystemLogResponse::from).getContent();
    }
}
