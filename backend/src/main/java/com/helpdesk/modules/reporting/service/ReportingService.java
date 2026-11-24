package com.helpdesk.modules.reporting.service;

import com.helpdesk.common.exception.BusinessException;
import com.helpdesk.common.exception.ErrorCode;
import com.helpdesk.common.security.HelpdeskUserPrincipal;
import com.helpdesk.modules.identity.entity.SupportTeam;
import com.helpdesk.modules.identity.service.IdentityDirectoryService;
import com.helpdesk.modules.reporting.dto.ReportingDtos.CategoryCountDto;
import com.helpdesk.modules.reporting.dto.ReportingDtos.CsatMetricsDto;
import com.helpdesk.modules.reporting.dto.ReportingDtos.RecentSlaAlertDto;
import com.helpdesk.modules.reporting.dto.ReportingDtos.ReportOverviewDto;
import com.helpdesk.modules.reporting.dto.ReportingDtos.ReportPeriodDto;
import com.helpdesk.modules.reporting.dto.ReportingDtos.ReportScopeDto;
import com.helpdesk.modules.reporting.dto.ReportingDtos.ScoreCountDto;
import com.helpdesk.modules.reporting.dto.ReportingDtos.SlaMetricsDto;
import com.helpdesk.modules.reporting.dto.ReportingDtos.StatusCountDto;
import com.helpdesk.modules.reporting.dto.ReportingDtos.TicketMetricsDto;
import com.helpdesk.modules.reporting.repository.ReportingQueryRepository;
import com.helpdesk.modules.reporting.repository.ReportingQueryRepository.CsatAggregateRow;
import com.helpdesk.modules.reporting.repository.ReportingQueryRepository.SlaAggregateRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ReportingService {

    private static final int MIN_DAYS = 1;
    private static final int MAX_DAYS = 365;
    private static final int DEFAULT_DAYS = 30;
    private static final int RECENT_ALERTS_LIMIT = 10;
    private static final String DISPLAY_TIMEZONE = "Asia/Ho_Chi_Minh";

    private static final List<String> ALL_STATUSES = List.of(
            "NEW", "ASSIGNED", "IN_PROGRESS", "WAITING_CUSTOMER", "RESOLVED", "CLOSED", "CANCELLED"
    );
    private static final Set<String> OPEN_STATUSES = Set.of(
            "NEW", "ASSIGNED", "IN_PROGRESS", "WAITING_CUSTOMER"
    );

    private final ReportingQueryRepository reportingQueryRepository;
    private final IdentityDirectoryService identityDirectoryService;
    private final Clock clock;

    public ReportingService(
            ReportingQueryRepository reportingQueryRepository,
            IdentityDirectoryService identityDirectoryService,
            Clock clock
    ) {
        this.reportingQueryRepository = reportingQueryRepository;
        this.identityDirectoryService = identityDirectoryService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ReportOverviewDto getOverview(HelpdeskUserPrincipal principal, Integer daysParam, Long requestedTeamId) {
        int days = daysParam != null ? daysParam : DEFAULT_DAYS;
        if (days < MIN_DAYS || days > MAX_DAYS) {
            throw BusinessException.unprocessable(
                    ErrorCode.INVALID_REPORT_DAYS,
                    "Tham số days phải nằm trong khoảng từ " + MIN_DAYS + " đến " + MAX_DAYS + "."
            );
        }

        Instant asOf = Instant.now(clock);
        Instant from = asOf.minus(days, ChronoUnit.DAYS);
        ReportPeriodDto period = new ReportPeriodDto(days, from, asOf, DISPLAY_TIMEZONE);

        ResolvedScope scope = resolveScope(principal, requestedTeamId);
        if (scope.emptyResult()) {
            return buildEmptyOverview(period, scope.dto());
        }

        List<Long> teamIds = scope.teamIds();

        // 1. Ticket metrics
        Map<String, Long> rawStatusCounts = reportingQueryRepository.countTicketsByStatus(from, asOf, teamIds)
                .stream()
                .collect(Collectors.toMap(StatusCountDto::status, StatusCountDto::count, Long::sum));
        List<StatusCountDto> byStatus = ALL_STATUSES.stream()
                .map(s -> new StatusCountDto(s, rawStatusCounts.getOrDefault(s, 0L)))
                .toList();
        long totalTickets = byStatus.stream().mapToLong(StatusCountDto::count).sum();
        long openTickets = byStatus.stream()
                .filter(s -> OPEN_STATUSES.contains(s.status()))
                .mapToLong(StatusCountDto::count)
                .sum();
        List<CategoryCountDto> byCategory = reportingQueryRepository.countTicketsByCategory(from, asOf, teamIds);
        TicketMetricsDto ticketMetrics = new TicketMetricsDto(totalTickets, openTickets, byStatus, byCategory);

        // 2. SLA metrics
        SlaAggregateRow slaAgg = reportingQueryRepository.aggregateSlaMetrics(from, asOf, teamIds);
        Double complianceRate = (slaAgg != null && slaAgg.eligibleSlaCount() > 0)
                ? round2((double) slaAgg.metSlaCount() * 100.0 / (double) slaAgg.eligibleSlaCount())
                : null;
        List<RecentSlaAlertDto> recentAlerts = reportingQueryRepository.findRecentSlaAlerts(
                from, asOf, teamIds, RECENT_ALERTS_LIMIT
        );
        SlaMetricsDto slaMetrics = new SlaMetricsDto(
                slaAgg != null ? round1(slaAgg.avgFirstResponseSeconds()) : null,
                slaAgg != null ? round1(slaAgg.avgNetResolutionSeconds()) : null,
                complianceRate,
                slaAgg != null ? slaAgg.eligibleSlaCount() : 0L,
                slaAgg != null ? slaAgg.metSlaCount() : 0L,
                slaAgg != null ? slaAgg.overdueTicketCount() : 0L,
                recentAlerts
        );

        // 3. CSAT metrics
        CsatAggregateRow csatAgg = reportingQueryRepository.aggregateCsatMetrics(from, asOf, teamIds);
        Map<Integer, Long> rawDist = reportingQueryRepository.countCsatDistribution(from, asOf, teamIds)
                .stream()
                .collect(Collectors.toMap(ScoreCountDto::score, ScoreCountDto::count, Long::sum));
        List<ScoreCountDto> distribution = List.of(5, 4, 3, 2, 1).stream()
                .map(score -> new ScoreCountDto(score, rawDist.getOrDefault(score, 0L)))
                .toList();
        CsatMetricsDto csatMetrics = new CsatMetricsDto(
                (csatAgg != null && csatAgg.submittedCount() > 0) ? round2(csatAgg.averageScore()) : null,
                csatAgg != null ? csatAgg.submittedCount() : 0L,
                distribution
        );

        return new ReportOverviewDto(period, scope.dto(), ticketMetrics, slaMetrics, csatMetrics);
    }

    private ResolvedScope resolveScope(HelpdeskUserPrincipal principal, Long requestedTeamId) {
        if (principal.hasRole("ADMIN")) {
            if (requestedTeamId == null) {
                return new ResolvedScope(null, new ReportScopeDto(null, "Toàn hệ thống"), false);
            }
            SupportTeam team = identityDirectoryService.requireTeamById(requestedTeamId);
            return new ResolvedScope(List.of(team.getId()), new ReportScopeDto(team.getId(), team.getName()), false);
        }

        // SUPERVISOR chỉ được xem các nhóm mình phụ trách
        List<Long> supervisedIds = identityDirectoryService.findSupervisedTeamIds(principal.userId());
        if (requestedTeamId != null) {
            if (!supervisedIds.contains(requestedTeamId)) {
                throw BusinessException.forbidden(
                        ErrorCode.TEAM_SCOPE_DENIED,
                        "Supervisor không có quyền xem báo cáo của nhóm teamId=" + requestedTeamId
                );
            }
            SupportTeam team = identityDirectoryService.requireTeamById(requestedTeamId);
            return new ResolvedScope(List.of(team.getId()), new ReportScopeDto(team.getId(), team.getName()), false);
        }

        if (supervisedIds.isEmpty()) {
            return new ResolvedScope(List.of(), new ReportScopeDto(null, "Chưa được phân công nhóm"), true);
        }
        if (supervisedIds.size() == 1) {
            SupportTeam team = identityDirectoryService.requireTeamById(supervisedIds.get(0));
            return new ResolvedScope(supervisedIds, new ReportScopeDto(team.getId(), team.getName()), false);
        }
        return new ResolvedScope(supervisedIds, new ReportScopeDto(null, "Các nhóm phụ trách"), false);
    }

    private ReportOverviewDto buildEmptyOverview(ReportPeriodDto period, ReportScopeDto scopeDto) {
        List<StatusCountDto> zeroStatuses = ALL_STATUSES.stream()
                .map(s -> new StatusCountDto(s, 0L))
                .toList();
        List<ScoreCountDto> zeroDist = List.of(5, 4, 3, 2, 1).stream()
                .map(score -> new ScoreCountDto(score, 0L))
                .toList();
        return new ReportOverviewDto(
                period,
                scopeDto,
                new TicketMetricsDto(0L, 0L, zeroStatuses, List.of()),
                new SlaMetricsDto(null, null, null, 0L, 0L, 0L, List.of()),
                new CsatMetricsDto(null, 0L, zeroDist)
        );
    }

    private Double round1(Double value) {
        if (value == null) {
            return null;
        }
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private Double round2(Double value) {
        if (value == null) {
            return null;
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private record ResolvedScope(List<Long> teamIds, ReportScopeDto dto, boolean emptyResult) {
    }
}
