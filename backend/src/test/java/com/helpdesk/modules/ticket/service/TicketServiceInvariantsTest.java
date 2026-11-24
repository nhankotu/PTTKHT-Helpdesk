package com.helpdesk.modules.ticket.service;

import com.helpdesk.common.exception.BusinessException;
import com.helpdesk.common.exception.ErrorCode;
import com.helpdesk.common.exception.OptimisticLockConflictException;
import com.helpdesk.common.security.HelpdeskUserPrincipal;
import com.helpdesk.modules.identity.service.IdentityDirectoryService;
import com.helpdesk.modules.ticket.adapter.OutboundChannelAdapter;
import com.helpdesk.modules.ticket.dto.StaffTicketDtos.StaffMessageCreateRequest;
import com.helpdesk.modules.ticket.dto.StaffTicketDtos.StaffMessageCreateResponse;
import com.helpdesk.modules.ticket.dto.StaffTicketDtos.StatusUpdateRequest;
import com.helpdesk.modules.ticket.entity.MessageDeliveryStatus;
import com.helpdesk.modules.ticket.entity.MessageVisibility;
import com.helpdesk.modules.ticket.entity.Ticket;
import com.helpdesk.modules.ticket.entity.TicketMessage;
import com.helpdesk.modules.ticket.entity.TicketStatus;
import com.helpdesk.modules.ticket.event.TicketPublicReplySentEvent;
import com.helpdesk.modules.ticket.repository.SatisfactionRatingRepository;
import com.helpdesk.modules.ticket.repository.TicketEventRepository;
import com.helpdesk.modules.ticket.repository.TicketMessageRepository;
import com.helpdesk.modules.ticket.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketServiceInvariantsTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private TicketMessageRepository ticketMessageRepository;
    @Mock private TicketEventRepository ticketEventRepository;
    @Mock private SatisfactionRatingRepository satisfactionRatingRepository;
    @Mock private OutboundChannelAdapter outboundChannelAdapter;
    @Mock private IdentityDirectoryService identityDirectoryService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private TicketService ticketService;
    private final Instant fixedNow = Instant.parse("2026-11-21T16:00:00Z");

    @BeforeEach
    void setUp() {
        ticketService = new TicketService(
                ticketRepository,
                ticketMessageRepository,
                ticketEventRepository,
                satisfactionRatingRepository,
                new TicketStateMachineGuard(),
                outboundChannelAdapter,
                identityDirectoryService,
                eventPublisher,
                Clock.fixed(fixedNow, ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("Cách ly INTERNAL: Customer chỉ gọi truy vấn PUBLIC và bị chặn 403 nếu xem ticket người khác")
    void customerOnlySeesPublicMessagesAndOwnTickets() {
        Ticket ticket = createTicket(10L, 100L, TicketStatus.IN_PROGRESS, 1);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(ticketMessageRepository.findPublicMessagesByTicketId(10L)).thenReturn(List.of());

        HelpdeskUserPrincipal owner = new HelpdeskUserPrincipal(5L, "owner@ex.com", Set.of("CUSTOMER"), Set.of(), 100L);
        ticketService.listCustomerPublicMessages(owner, 10L);

        verify(ticketMessageRepository).findPublicMessagesByTicketId(10L);
        verify(ticketMessageRepository, never()).findByTicketIdOrderByOccurredAtAscIdAsc(any());

        HelpdeskUserPrincipal otherCustomer = new HelpdeskUserPrincipal(6L, "other@ex.com", Set.of("CUSTOMER"), Set.of(), 999L);
        assertThatThrownBy(() -> ticketService.listCustomerPublicMessages(otherCustomer, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TICKET_ACCESS_DENIED);
    }

    @Test
    @DisplayName("Optimistic Locking: Cập nhật trạng thái với version cũ trả 409 OPTIMISTIC_LOCK_CONFLICT kèm currentVersion")
    void staleVersionThrows409Conflict() {
        Ticket ticket = createTicket(10L, 100L, TicketStatus.IN_PROGRESS, 3);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));

        HelpdeskUserPrincipal agent = new HelpdeskUserPrincipal(12L, "agent@ex.com", Set.of("AGENT"), Set.of(2L), null);
        StatusUpdateRequest staleReq = new StatusUpdateRequest(2, TicketStatus.WAITING_CUSTOMER, null, null);

        assertThatThrownBy(() -> ticketService.updateStatus(agent, 10L, staleReq))
                .isInstanceOf(OptimisticLockConflictException.class)
                .satisfies(ex -> assertThat(((OptimisticLockConflictException) ex).getCurrentVersion()).isEqualTo(3));
    }

    @Test
    @DisplayName("UC03-E1: Khi gửi tin PUBLIC qua kênh lỗi (FAILED), KHÔNG đổi trạng thái và KHÔNG phát sự kiện FIRST_RESPONSE")
    void failedOutboundDeliveryDoesNotTransitionOrTriggerSla() {
        Ticket ticket = createTicket(10L, 100L, TicketStatus.IN_PROGRESS, 1);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(ticketMessageRepository.save(any(TicketMessage.class))).thenAnswer(inv -> {
            TicketMessage msg = inv.getArgument(0);
            ReflectionTestUtils.setField(msg, "id", 55L);
            return msg;
        });
        when(outboundChannelAdapter.deliver(any(), any())).thenReturn(MessageDeliveryStatus.FAILED);

        HelpdeskUserPrincipal agent = new HelpdeskUserPrincipal(12L, "agent@ex.com", Set.of("AGENT"), Set.of(2L), null);
        StaffMessageCreateRequest req = new StaffMessageCreateRequest(
                MessageVisibility.PUBLIC, "Phản hồi lỗi mạng", TicketStatus.WAITING_CUSTOMER, null, 1
        );

        StaffMessageCreateResponse res = ticketService.postStaffMessage(agent, 10L, req);

        assertThat(res.message().deliveryStatus()).isEqualTo(MessageDeliveryStatus.FAILED);
        assertThat(res.ticketStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        verify(eventPublisher, never()).publishEvent(any(TicketPublicReplySentEvent.class));
    }

    private Ticket createTicket(Long id, Long customerId, TicketStatus status, int version) {
        Ticket t = new Ticket("HD-2026-TEST01", 1L, customerId, null, 1L, 1L, "Test", "Body", fixedNow);
        t.setStatus(status);
        t.setTeamId(2L);
        t.setAssignedAgentId(12L);
        ReflectionTestUtils.setField(t, "id", id);
        ReflectionTestUtils.setField(t, "version", version);
        return t;
    }
}
