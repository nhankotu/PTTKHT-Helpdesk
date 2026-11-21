package com.helpdesk.modules.reporting.service;

import com.helpdesk.common.exception.BusinessException;
import com.helpdesk.common.exception.ErrorCode;
import com.helpdesk.common.security.HelpdeskUserPrincipal;
import com.helpdesk.modules.identity.entity.SupportTeam;
import com.helpdesk.modules.identity.service.IdentityDirectoryService;
import com.helpdesk.modules.reporting.dto.ReportingDtos.CategoryCountDto;
import com.helpdesk.modules.reporting.dto.ReportingDtos.ReportOverviewDto;
import com.helpdesk.modules.reporting.dto.ReportingDtos.ScoreCountDto;
import com.helpdesk.modules.reporting.dto.ReportingDtos.StatusCountDto;
import com.helpdesk.modules.reporting.repository.ReportingQueryRepository;
import com.helpdesk.modules.reporting.repository.ReportingQueryRepository.CsatAggregateRow;
import com.helpdesk.modules.reporting.repository.ReportingQueryRepository.SlaAggregateRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportingServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-11-22T02:00:00Z");

    @Mock
    private ReportingQueryRepository reportingQueryRepository;
    @Mock
    private IdentityDirectoryService identityDirectoryService;

    private ReportingService reportingService;

    @BeforeEach
    void setUp() {
        reportingService = new ReportingService(
                reportingQueryRepository,
                identityDirectoryService,
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("UC08: Tham số days ngoài biên [1..365] trả 422 INVALID_REPORT_DAYS")
    void invalidDaysThrowsUnprocessable() {
        HelpdeskUserPrincipal admin = new HelpdeskUserPrincipal(1L, "admin@helpdesk.local", Set.of("ADMIN"), Set.of(), null);

        assertThatThrownBy(() -> reportingService.getOverview(admin, 0, null))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REPORT_DAYS);

        assertThatThrownBy(() -> reportingService.getOverview(admin, 366, null))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REPORT_DAYS);
    }

    @Test
    @DisplayName("UC08: Supervisor truyền teamId ngoài nhóm quản lý bị chặn 403 TEAM_SCOPE_DENIED")
    void supervisorAccessingOtherTeamIsDenied() {
        HelpdeskUserPrincipal supervisor = new HelpdeskUserPrincipal(2L, "sup@helpdesk.local", Set.of("SUPERVISOR"), Set.of(10L), null);
        when(identityDirectoryService.findSupervisedTeamIds(2L)).thenReturn(List.of(10L));

        assertThatThrownBy(() -> reportingService.getOverview(supervisor, 30, 99L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TEAM_SCOPE_DENIED);
    }

    @Test
    @DisplayName("UC08-E2: Kỳ báo cáo không có dữ liệu trả về 0 cho bộ đếm và null cho trung bình/tỷ lệ")
    void emptyPeriodReturnsZerosAndNullAverages() {
        HelpdeskUserPrincipal admin = new HelpdeskUserPrincipal(1L, "admin@helpdesk.local", Set.of("ADMIN"), Set.of(), null);
        when(reportingQueryRepository.countTicketsByStatus(any(), any(), isNull())).thenReturn(List.of());
        when(reportingQueryRepository.countTicketsByCategory(any(), any(), isNull())).thenReturn(List.of());
        when(reportingQueryRepository.aggregateSlaMetrics(any(), any(), isNull()))
                .thenReturn(new SlaAggregateRow(null, null, 0L, 0L, 0L));
        when(reportingQueryRepository.findRecentSlaAlerts(any(), any(), isNull(), eq(10))).thenReturn(List.of());
        when(reportingQueryRepository.aggregateCsatMetrics(any(), any(), isNull()))
                .thenReturn(new CsatAggregateRow(null, 0L));
        when(reportingQueryRepository.countCsatDistribution(any(), any(), isNull())).thenReturn(List.of());

        ReportOverviewDto result = reportingService.getOverview(admin, 7, null);

        assertThat(result.period().days()).isEqualTo(7);
        assertThat(result.scope().teamName()).isEqualTo("Toàn hệ thống");
        assertThat(result.ticketMetrics().totalTickets()).isZero();
        assertThat(result.ticketMetrics().byStatus()).hasSize(7);
        assertThat(result.slaMetrics().avgFirstResponseSeconds()).isNull();
        assertThat(result.slaMetrics().avgNetResolutionSeconds()).isNull();
        assertThat(result.slaMetrics().complianceRatePercent()).isNull();
        assertThat(result.csatMetrics().averageScore()).isNull();
        assertThat(result.csatMetrics().submittedCount()).isZero();
        assertThat(result.csatMetrics().distribution()).hasSize(5);
    }

    @Test
    @DisplayName("UC08: Tổng hợp đúng KPI Ticket, SLA compliance rate và CSAT cho Supervisor trong phạm vi nhóm")
    void supervisorOverviewComputesKpisCorrectly() {
        HelpdeskUserPrincipal supervisor = new HelpdeskUserPrincipal(2L, "sup@helpdesk.local", Set.of("SUPERVISOR"), Set.of(10L), null);
        SupportTeam team = mock(SupportTeam.class);
        when(team.getId()).thenReturn(10L);
        when(team.getName()).thenReturn("Nhóm Thanh toán");

        when(identityDirectoryService.findSupervisedTeamIds(2L)).thenReturn(List.of(10L));
        when(identityDirectoryService.requireTeamById(10L)).thenReturn(team);

        List<Long> teamScope = List.of(10L);
        when(reportingQueryRepository.countTicketsByStatus(any(), any(), eq(teamScope))).thenReturn(List.of(
                new StatusCountDto("NEW", 2),
                new StatusCountDto("IN_PROGRESS", 3),
                new StatusCountDto("CLOSED", 15)
        ));
        when(reportingQueryRepository.countTicketsByCategory(any(), any(), eq(teamScope))).thenReturn(List.of(
                new CategoryCountDto(1L, "PAYMENT", "Thanh toán", 20)
        ));
        when(reportingQueryRepository.aggregateSlaMetrics(any(), any(), eq(teamScope)))
                .thenReturn(new SlaAggregateRow(845.54, 12420.0, 40L, 37L, 1L));
        when(reportingQueryRepository.findRecentSlaAlerts(any(), any(), eq(teamScope), eq(10))).thenReturn(List.of());
        when(reportingQueryRepository.aggregateCsatMetrics(any(), any(), eq(teamScope)))
                .thenReturn(new CsatAggregateRow(4.624, 16L));
        when(reportingQueryRepository.countCsatDistribution(any(), any(), eq(teamScope))).thenReturn(List.of(
                new ScoreCountDto(5, 12),
                new ScoreCountDto(4, 4)
        ));

        ReportOverviewDto result = reportingService.getOverview(supervisor, 30, null);

        assertThat(result.scope().teamId()).isEqualTo(10L);
        assertThat(result.scope().teamName()).isEqualTo("Nhóm Thanh toán");
        assertThat(result.ticketMetrics().totalTickets()).isEqualTo(20L);
        assertThat(result.ticketMetrics().openTickets()).isEqualTo(5L);
        assertThat(result.slaMetrics().avgFirstResponseSeconds()).isEqualTo(845.5);
        assertThat(result.slaMetrics().complianceRatePercent()).isEqualTo(92.5);
        assertThat(result.slaMetrics().overdueTicketCount()).isEqualTo(1L);
        assertThat(result.csatMetrics().averageScore()).isEqualTo(4.62);
        assertThat(result.csatMetrics().submittedCount()).isEqualTo(16L);
    }
}
