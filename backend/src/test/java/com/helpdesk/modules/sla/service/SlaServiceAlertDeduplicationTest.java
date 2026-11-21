package com.helpdesk.modules.sla.service;

import com.helpdesk.modules.identity.service.IdentityDirectoryService;
import com.helpdesk.modules.sla.entity.SlaEvent;
import com.helpdesk.modules.sla.entity.SlaEventType;
import com.helpdesk.modules.sla.entity.SlaMetric;
import com.helpdesk.modules.sla.entity.SlaPause;
import com.helpdesk.modules.sla.entity.SlaPauseReason;
import com.helpdesk.modules.sla.entity.SlaTrackingStatus;
import com.helpdesk.modules.sla.entity.TicketSla;
import com.helpdesk.modules.sla.repository.EscalationActionRepository;
import com.helpdesk.modules.sla.repository.SlaEventRepository;
import com.helpdesk.modules.sla.repository.SlaPauseRepository;
import com.helpdesk.modules.sla.repository.SlaPolicyRepository;
import com.helpdesk.modules.sla.repository.SlaPolicyTargetRepository;
import com.helpdesk.modules.sla.repository.TicketSlaRepository;
import com.helpdesk.modules.ticket.entity.TicketPriority;
import com.helpdesk.modules.ticket.entity.TicketStatus;
import com.helpdesk.modules.ticket.event.TicketStatusChangedEvent;
import com.helpdesk.modules.ticket.service.TicketRoutingPort;
import com.helpdesk.modules.ticket.service.TicketRoutingPort.TicketSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlaServiceAlertDeduplicationTest {

    @Mock private SlaPolicyRepository slaPolicyRepository;
    @Mock private SlaPolicyTargetRepository slaPolicyTargetRepository;
    @Mock private TicketSlaRepository ticketSlaRepository;
    @Mock private SlaPauseRepository slaPauseRepository;
    @Mock private SlaEventRepository slaEventRepository;
    @Mock private EscalationActionRepository escalationActionRepository;
    @Mock private TicketRoutingPort ticketRoutingPort;
    @Mock private IdentityDirectoryService identityDirectoryService;
    @Mock private NotificationPort notificationPort;

    private SlaService slaService;
    private final Instant start = Instant.parse("2026-11-21T10:00:00Z");

    @BeforeEach
    void setUp() {
        // Giả lập thời điểm hiện tại là 10:50:00 (đã qua 83% của target 1h = 3600s)
        Clock scanClock = Clock.fixed(start.plusSeconds(3000), ZoneOffset.UTC);
        slaService = new SlaService(
                slaPolicyRepository,
                slaPolicyTargetRepository,
                ticketSlaRepository,
                slaPauseRepository,
                slaEventRepository,
                escalationActionRepository,
                ticketRoutingPort,
                identityDirectoryService,
                notificationPort,
                scanClock
        );
    }

    @Test
    @DisplayName("Ca kiểm thử #8: Chạy SLA Scheduler nhiều lần cho ticket vượt ngưỡng 80% chỉ tạo đúng 1 sla_event WARNING")
    void schedulerDoesNotDuplicateWarningEvent() {
        TicketSla sla = new TicketSla(
                10L, 1L, SlaMetric.FIRST_RESPONSE, 3600, new BigDecimal("0.8000"),
                start, start.plusSeconds(3600)
        );
        ReflectionTestUtils.setField(sla, "id", 100L);

        when(ticketSlaRepository.findByTrackingStatus(SlaTrackingStatus.RUNNING)).thenReturn(List.of(sla));
        when(slaEventRepository.existsByTicketSlaIdAndEventType(100L, SlaEventType.WARNING))
                .thenReturn(false)
                .thenReturn(true);
        when(slaEventRepository.save(any(SlaEvent.class))).thenAnswer(inv -> {
            SlaEvent e = inv.getArgument(0);
            ReflectionTestUtils.setField(e, "id", 500L);
            return e;
        });
        when(ticketRoutingPort.requireTicketSnapshot(10L)).thenReturn(new TicketSnapshot(
                10L, "HD-1", "Test", "Body", TicketStatus.ASSIGNED, TicketPriority.HIGH, 1L, 2L, 12L, start, 1
        ));
        when(identityDirectoryService.findSupervisorIdOfTeam(2L)).thenReturn(Optional.of(5L));

        int firstRun = slaService.scanAndTriggerAlerts();
        int secondRun = slaService.scanAndTriggerAlerts();

        assertThat(firstRun).isEqualTo(1);
        assertThat(secondRun).isZero();
        verify(slaEventRepository, times(1)).save(any(SlaEvent.class));
        verify(notificationPort, times(1)).notifySlaEvent(12L, 10L, 500L);
    }

    @Test
    @DisplayName("Chuyển WAITING_CUSTOMER -> IN_PROGRESS đóng sla_pause và cộng bù thời gian vào deadline_at")
    void pauseAndResumeExtendsResolutionDeadline() {
        TicketSla resSla = new TicketSla(
                10L, 2L, SlaMetric.RESOLUTION, 14400, new BigDecimal("0.8000"),
                start, start.plusSeconds(14400)
        );
        ReflectionTestUtils.setField(resSla, "id", 200L);
        resSla.setTrackingStatus(SlaTrackingStatus.PAUSED);

        SlaPause openPause = new SlaPause(200L, start.plusSeconds(1000), null, SlaPauseReason.WAITING_CUSTOMER);
        when(ticketSlaRepository.findByTicketIdAndMetric(10L, SlaMetric.RESOLUTION)).thenReturn(Optional.of(resSla));
        when(slaPauseRepository.findByTicketSlaIdAndResumedAtIsNull(200L)).thenReturn(Optional.of(openPause));

        Instant resumeAt = start.plusSeconds(1600); // pause 600 giây
        slaService.onTicketStatusChanged(new TicketStatusChangedEvent(10L, TicketStatus.WAITING_CUSTOMER, TicketStatus.IN_PROGRESS, resumeAt));

        assertThat(resSla.getTrackingStatus()).isEqualTo(SlaTrackingStatus.RUNNING);
        assertThat(resSla.getDeadlineAt()).isEqualTo(start.plusSeconds(14400 + 600));
        assertThat(openPause.getResumedAt()).isEqualTo(resumeAt);
    }
}
