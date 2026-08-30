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
 * (BACKEND_PLAN.md §8). Per §4's access map, Managing Director's only
 * module is general-report (read-only, farm-wide), and Administrator has
 * everything — so both roles, and only those two, can reach this. Fixed in
 * Phase 6 after the frontend wiring pass flagged that this used to be
 * Administrator-only, which 403'd the very role this view exists for.
 */
@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasAnyRole('ADMINISTRATOR', 'MANAGING_DIRECTOR')")
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
