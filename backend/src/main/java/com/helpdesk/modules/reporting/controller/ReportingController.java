package com.helpdesk.modules.reporting.controller;

import com.helpdesk.common.security.HelpdeskUserPrincipal;
import com.helpdesk.common.security.SecurityUtils;
import com.helpdesk.modules.reporting.dto.ReportingDtos.ReportOverviewDto;
import com.helpdesk.modules.reporting.service.ReportingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "6C. Reporting & KPI Dashboard (UC08)", description = "Báo cáo hợp nhất Ticket, SLA (v_ticket_sla_summary) và CSAT theo phạm vi nhóm")
public class ReportingController {

    private final ReportingService reportingService;

    public ReportingController(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    @Operation(summary = "Báo cáo tổng quan hợp nhất 3 khối KPI (Ticket, SLA, CSAT) trên cùng mốc asOf")
    public ResponseEntity<ReportOverviewDto> getOverview(
            @RequestParam(required = false, defaultValue = "30") Integer days,
            @RequestParam(required = false) Long teamId
    ) {
        HelpdeskUserPrincipal principal = SecurityUtils.requireCurrentPrincipal();
        return ResponseEntity.ok(reportingService.getOverview(principal, days, teamId));
    }
}
