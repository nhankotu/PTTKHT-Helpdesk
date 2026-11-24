package com.helpdesk.modules.routing.service;

import com.helpdesk.modules.identity.entity.Category;
import com.helpdesk.modules.identity.service.IdentityDirectoryService;
import com.helpdesk.modules.routing.classifier.TicketClassifier;
import com.helpdesk.modules.routing.classifier.TicketClassifier.ClassificationSuggestion;
import com.helpdesk.modules.routing.dto.RoutingDtos.ClassificationResultDto;
import com.helpdesk.modules.routing.entity.ClassificationOutcome;
import com.helpdesk.modules.routing.entity.ClassificationResult;
import com.helpdesk.modules.routing.entity.RoutingRule;
import com.helpdesk.modules.routing.repository.AssignmentHistoryRepository;
import com.helpdesk.modules.routing.repository.ClassificationResultRepository;
import com.helpdesk.modules.routing.repository.RoutingRuleRepository;
import com.helpdesk.modules.routing.strategy.LeastLoadedAgentStrategy;
import com.helpdesk.modules.sla.service.NotificationPort;
import com.helpdesk.modules.ticket.entity.TicketPriority;
import com.helpdesk.modules.ticket.entity.TicketStatus;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoutingServiceFallbackTest {

    @Mock private RoutingRuleRepository routingRuleRepository;
    @Mock private ClassificationResultRepository classificationResultRepository;
    @Mock private AssignmentHistoryRepository assignmentHistoryRepository;
    @Mock private TicketClassifier ticketClassifier;
    @Mock private TicketRoutingPort ticketRoutingPort;
    @Mock private IdentityDirectoryService identityDirectoryService;
    @Mock private NotificationPort notificationPort;

    private RoutingService routingService;
    private final Instant fixedNow = Instant.parse("2026-11-21T16:00:00Z");

    @BeforeEach
    void setUp() {
        routingService = new RoutingService(
                routingRuleRepository,
                classificationResultRepository,
                assignmentHistoryRepository,
                ticketClassifier,
                new LeastLoadedAgentStrategy(),
                ticketRoutingPort,
                identityDirectoryService,
                notificationPort,
                Clock.fixed(fixedNow, ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("Ca kiểm thử #5a: Khi AI Classifier lỗi/timeout, lưu FAILED và giữ ticket ở hàng chờ Supervisor")
    void classifierExceptionFallsBackToSupervisorQueue() {
        TicketSnapshot snapshot = new TicketSnapshot(
                10L, "HD-2026-001", "Lỗi thanh toán", "Chi tiết", TicketStatus.NEW,
                TicketPriority.NORMAL, null, null, null, fixedNow, 0
        );
        when(ticketRoutingPort.requireTicketSnapshot(10L)).thenReturn(snapshot);
        when(identityDirectoryService.findAllActiveCategories()).thenReturn(List.of());
        when(ticketClassifier.classify(any(), any(), any())).thenThrow(new RuntimeException("OpenAI timeout"));
        when(classificationResultRepository.save(any(ClassificationResult.class))).thenAnswer(inv -> inv.getArgument(0));

        ClassificationResultDto res = routingService.classifyAndAutoRoute(10L);

        assertThat(res.outcome()).isEqualTo(ClassificationOutcome.FAILED);
        assertThat(res.failureReason()).contains("OpenAI timeout");
        verify(ticketRoutingPort, never()).applyAssignment(any(), anyInt(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Ca kiểm thử #5b: Khi không có Agent khả dụng trong nhóm đích, lưu REVIEW_REQUIRED và giữ NEW")
    void noAvailableAgentFallsBackToSupervisorQueue() {
        TicketSnapshot snapshot = new TicketSnapshot(
                11L, "HD-2026-002", "Lỗi chuyển khoản", "Chi tiết", TicketStatus.NEW,
                TicketPriority.HIGH, null, null, null, fixedNow, 0
        );
        Category paymentCat = new Category("PAYMENT", "Thanh toán");
        ReflectionTestUtils.setField(paymentCat, "id", 1L);

        when(ticketRoutingPort.requireTicketSnapshot(11L)).thenReturn(snapshot);
        when(identityDirectoryService.findAllActiveCategories()).thenReturn(List.of(paymentCat));
        when(ticketClassifier.classify(any(), any(), any())).thenReturn(
                new ClassificationSuggestion("PAYMENT", new BigDecimal("0.9200"), "gpt-4o-mini", "Match")
        );
        when(identityDirectoryService.findCategoryByCode("PAYMENT")).thenReturn(Optional.of(paymentCat));
        when(routingRuleRepository.findByCategoryIdAndActiveTrue(1L))
                .thenReturn(Optional.of(new RoutingRule(1L, 2L, new BigDecimal("0.7500"), true, true)));
        when(identityDirectoryService.findAvailableActiveAgentIdsInTeam(2L)).thenReturn(List.of());
        when(classificationResultRepository.save(any(ClassificationResult.class))).thenAnswer(inv -> inv.getArgument(0));

        ClassificationResultDto res = routingService.classifyAndAutoRoute(11L);

        assertThat(res.outcome()).isEqualTo(ClassificationOutcome.REVIEW_REQUIRED);
        verify(ticketRoutingPort, never()).applyAssignment(any(), anyInt(), any(), any(), any(), any(), any());
    }
}
