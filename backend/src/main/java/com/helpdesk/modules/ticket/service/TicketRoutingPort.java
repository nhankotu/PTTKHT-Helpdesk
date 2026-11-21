package com.helpdesk.modules.ticket.service;

import com.helpdesk.common.dto.PageResponse;
import com.helpdesk.modules.ticket.entity.TicketPriority;
import com.helpdesk.modules.ticket.entity.TicketStatus;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;

/**
 * Port giao tiếp công khai của module ticket cho module routing (UC04) và sla (UC05).
 * Đảm bảo Modular Monolith không truy cập chéo TicketRepository.
 */
public interface TicketRoutingPort {

    record TicketSnapshot(
            Long id,
            String ticketCode,
            String subject,
            String description,
            TicketStatus status,
            TicketPriority priority,
            Long categoryId,
            Long teamId,
            Long assignedAgentId,
            Instant createdAt,
            int version
    ) {
    }

    TicketSnapshot requireTicketSnapshot(Long ticketId);

    Map<Long, Long> countOpenTicketsByAgents(Collection<Long> agentIds);

    PageResponse<TicketSnapshot> listUnassignedNewTickets(Pageable pageable);

    TicketSnapshot applyAssignment(
            Long ticketId,
            int expectedVersion,
            Long categoryId,
            Long teamId,
            Long agentId,
            Long actorId,
            String reason
    );
}
