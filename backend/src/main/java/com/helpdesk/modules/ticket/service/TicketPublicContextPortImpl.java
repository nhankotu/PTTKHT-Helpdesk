package com.helpdesk.modules.ticket.service;

import com.helpdesk.common.exception.BusinessException;
import com.helpdesk.common.exception.ErrorCode;
import com.helpdesk.common.security.SecurityUtils;
import com.helpdesk.modules.ticket.entity.Ticket;
import com.helpdesk.modules.ticket.entity.TicketMessage;
import com.helpdesk.modules.ticket.repository.TicketMessageRepository;
import com.helpdesk.modules.ticket.repository.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TicketPublicContextPortImpl implements TicketPublicContextPort {

    private final TicketRepository ticketRepository;
    private final TicketMessageRepository ticketMessageRepository;

    public TicketPublicContextPortImpl(
            TicketRepository ticketRepository,
            TicketMessageRepository ticketMessageRepository
    ) {
        this.ticketRepository = ticketRepository;
        this.ticketMessageRepository = ticketMessageRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public TicketPublicContext requirePublicContext(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> BusinessException.notFound("Ticket", ticketId));
        var principal = SecurityUtils.requireCurrentPrincipal();
        boolean staff = principal.hasRole("AGENT") || principal.hasRole("SUPERVISOR") || principal.hasRole("ADMIN");
        boolean assigned = principal.userId().equals(ticket.getAssignedAgentId());
        boolean team = ticket.getTeamId() != null && principal.isInTeam(ticket.getTeamId());
        boolean unassigned = ticket.getTeamId() == null && ticket.getAssignedAgentId() == null;
        if (!staff || (!principal.hasRole("ADMIN") && !assigned && !team && !unassigned))
            throw BusinessException.forbidden(ErrorCode.TICKET_ACCESS_DENIED, "Bạn không có quyền truy cập ticket này.");
        // HARD INVARIANT: Lọc cứng visibility = 'PUBLIC' ngay ở câu JPQL Repository
        List<TicketMessage> messages = ticketMessageRepository.findPublicMessagesByTicketId(ticketId);
        return new TicketPublicContext(ticket.getId(), ticket.getSubject(), ticket.getStatus(), messages.stream().map(TicketMessage::getBody).toList(),
                messages.stream().map(m -> new PublicMessage("message:" + m.getId(), m.getBody())).toList());
    }
}
