package com.helpdesk.modules.ticket.service;

import com.helpdesk.common.dto.PageResponse;
import com.helpdesk.common.exception.BusinessException;
import com.helpdesk.common.exception.OptimisticLockConflictException;
import com.helpdesk.modules.ticket.entity.Ticket;
import com.helpdesk.modules.ticket.entity.TicketEvent;
import com.helpdesk.modules.ticket.entity.TicketEventAction;
import com.helpdesk.modules.ticket.entity.TicketStatus;
import com.helpdesk.modules.ticket.repository.TicketEventRepository;
import com.helpdesk.modules.ticket.repository.TicketRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Service
public class TicketRoutingPortImpl implements TicketRoutingPort {

    private final TicketRepository ticketRepository;
    private final TicketEventRepository ticketEventRepository;
    private final Clock clock;

    public TicketRoutingPortImpl(
            TicketRepository ticketRepository,
            TicketEventRepository ticketEventRepository,
            Clock clock
    ) {
        this.ticketRepository = ticketRepository;
        this.ticketEventRepository = ticketEventRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public TicketSnapshot requireTicketSnapshot(Long ticketId) {
        Ticket t = ticketRepository.findById(ticketId)
                .orElseThrow(() -> BusinessException.notFound("Ticket", ticketId));
        return toSnapshot(t);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Long> countOpenTicketsByAgents(Collection<Long> agentIds) {
        Map<Long, Long> counts = new HashMap<>();
        if (agentIds == null || agentIds.isEmpty()) {
            return counts;
        }
        for (Long id : agentIds) {
            counts.put(id, 0L);
        }
        for (Object[] row : ticketRepository.countOpenTicketsByAgentIds(agentIds)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TicketSnapshot> listUnassignedNewTickets(Pageable pageable) {
        return PageResponse.from(
                ticketRepository.findByStatusAndAssignedAgentIdIsNullOrderByCreatedAtAsc(TicketStatus.NEW, pageable)
                        .map(this::toSnapshot)
        );
    }

    @Override
    @Transactional
    public TicketSnapshot applyAssignment(
            Long ticketId,
            int expectedVersion,
            Long categoryId,
            Long teamId,
            Long agentId,
            Long actorId,
            String reason
    ) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> BusinessException.notFound("Ticket", ticketId));
        if (ticket.getVersion() != expectedVersion) {
            throw new OptimisticLockConflictException(ticket.getVersion());
        }

        Instant now = Instant.now(clock);
        String oldVal = "status=" + ticket.getStatus() + ",team=" + ticket.getTeamId() + ",agent=" + ticket.getAssignedAgentId();
        if (categoryId != null) {
            ticket.setCategoryId(categoryId);
        }
        ticket.setTeamId(teamId);
        ticket.setAssignedAgentId(agentId);
        if (ticket.getStatus() == TicketStatus.NEW && agentId != null) {
            ticket.setStatus(TicketStatus.ASSIGNED);
        }

        Ticket saved = ticketRepository.saveAndFlush(ticket);
        String newVal = "status=" + saved.getStatus() + ",team=" + saved.getTeamId() + ",agent=" + saved.getAssignedAgentId();
        ticketEventRepository.save(new TicketEvent(
                saved.getId(), actorId, TicketEventAction.ASSIGNMENT_CHANGE, "assignment", oldVal, newVal, reason, now
        ));
        return toSnapshot(saved);
    }

    private TicketSnapshot toSnapshot(Ticket t) {
        return new TicketSnapshot(
                t.getId(), t.getTicketCode(), t.getSubject(), t.getDescription(),
                t.getStatus(), t.getPriority(), t.getCategoryId(), t.getTeamId(),
                t.getAssignedAgentId(), t.getCreatedAt(), t.getVersion()
        );
    }
}
