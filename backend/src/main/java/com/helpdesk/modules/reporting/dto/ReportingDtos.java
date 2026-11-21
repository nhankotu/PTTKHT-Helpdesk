package com.helpdesk.modules.reporting.dto;

import java.time.Instant;
import java.util.List;

public final class ReportingDtos {

    private ReportingDtos() {
    }

    public record ReportOverviewDto(
            ReportPeriodDto period,
            ReportScopeDto scope,
            TicketMetricsDto ticketMetrics,
            SlaMetricsDto slaMetrics,
            CsatMetricsDto csatMetrics
    ) {
    }

    public record ReportPeriodDto(
            int days,
            Instant from,
            Instant asOf,
            String displayTimezone
    ) {
    }

    public record ReportScopeDto(
            Long teamId,
            String teamName
    ) {
    }

    public record StatusCountDto(
            String status,
            long count
    ) {
    }

    public record CategoryCountDto(
            Long categoryId,
            String categoryCode,
            String categoryName,
            long count
    ) {
    }

    public record TicketMetricsDto(
            long totalTickets,
            long openTickets,
            List<StatusCountDto> byStatus,
            List<CategoryCountDto> byCategory
    ) {
    }

    public record RecentSlaAlertDto(
            Long slaEventId,
            Long ticketId,
            String ticketCode,
            String subject,
            String priority,
            String metric,
            String eventType,
            Instant deadlineAt,
            Instant createdAt,
            boolean handled
    ) {
    }

    public record SlaMetricsDto(
            Double avgFirstResponseSeconds,
            Double avgNetResolutionSeconds,
            Double complianceRatePercent,
            long eligibleSlaCount,
            long metSlaCount,
            long overdueTicketCount,
            List<RecentSlaAlertDto> recentAlerts
    ) {
    }

    public record ScoreCountDto(
            int score,
            long count
    ) {
    }

    public record CsatMetricsDto(
            Double averageScore,
            long submittedCount,
            List<ScoreCountDto> distribution
    ) {
    }
}
