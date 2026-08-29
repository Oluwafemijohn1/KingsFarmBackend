package com.kingsfarm.kingsfarmbackend.reports;

import com.kingsfarm.kingsfarmbackend.common.reports.ReportTableResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Backs GeneralReportView — the Managing Director's farm-wide summary
 * (BACKEND_PLAN.md §8). Administrator-only, matching the frontend's
 * {@code canExport={false}} + "Managing Director" framing: this is a
 * top-level view, not something any single module manager reaches.
 */
@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class GeneralReportController {

    private final GeneralReportService service;

    public GeneralReportController(GeneralReportService service) {
        this.service = service;
    }

    @GetMapping("/general")
    public ReportTableResponse general(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return service.general(start, end);
    }
}
