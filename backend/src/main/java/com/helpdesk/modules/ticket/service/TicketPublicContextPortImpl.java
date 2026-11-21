package com.helpdesk.modules.ticket.service;

import com.helpdesk.common.exception.BusinessException;
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
        // HARD INVARIANT: Lọc cứng visibility = 'PUBLIC' ngay ở câu JPQL Repository
        List<String> publicBodies = ticketMessageRepository.findPublicMessagesByTicketId(ticketId).stream()
                .map(TicketMessage::getBody)
                .toList();
        return new TicketPublicContext(ticket.getId(), ticket.getSubject(), ticket.getStatus(), publicBodies);
    }
}
