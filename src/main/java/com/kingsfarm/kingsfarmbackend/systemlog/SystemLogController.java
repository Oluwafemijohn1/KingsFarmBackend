package com.kingsfarm.kingsfarmbackend.systemlog;

import com.kingsfarm.kingsfarmbackend.common.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Backs AdminView's Security & Logs tab (Access / Activity / Audit sub-tabs) — Administrator-only. */
@RestController
@RequestMapping("/api/v1/admin/logs")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class SystemLogController {

    private final SystemLogService service;

    public SystemLogController(SystemLogService service) {
        this.service = service;
    }

    @GetMapping("/{type}")
    public PageResponse<SystemLogResponse> list(@PathVariable LogType type, @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(service.list(type, pageable).map(SystemLogResponse::from));
    }
}
