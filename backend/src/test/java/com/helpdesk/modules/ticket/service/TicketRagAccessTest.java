package com.helpdesk.modules.ticket.service;

import com.helpdesk.common.security.HelpdeskUserPrincipal;
import com.helpdesk.modules.ticket.entity.Ticket;
import com.helpdesk.modules.ticket.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TicketRagAccessTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void deniesOutsideTeamBeforeReadingMessages() {
        TicketRepository tickets = mock(TicketRepository.class);
        TicketMessageRepository messages = mock(TicketMessageRepository.class);
        Ticket ticket = new Ticket("HD-X", 1L, 2L, null, 1L, null, "subject", "description", Instant.EPOCH);
        ticket.setTeamId(99L); ticket.setAssignedAgentId(88L);
        when(tickets.findById(1L)).thenReturn(Optional.of(ticket));
        HelpdeskUserPrincipal principal = new HelpdeskUserPrincipal(10L, "agent@test", Set.of("AGENT"), Set.of(20L), null);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        assertThatThrownBy(() -> new TicketPublicContextPortImpl(tickets, messages).requirePublicContext(1L))
                .isInstanceOf(com.helpdesk.common.exception.BusinessException.class);
        verifyNoInteractions(messages);
    }
}
