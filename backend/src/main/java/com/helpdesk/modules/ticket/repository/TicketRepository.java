package com.helpdesk.modules.ticket.repository;

import com.helpdesk.modules.ticket.entity.Ticket;
import com.helpdesk.modules.ticket.entity.TicketPriority;
import com.helpdesk.modules.ticket.entity.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    Optional<Ticket> findByTicketCode(String ticketCode);

    Optional<Ticket> findFirstByConversationIdOrderByCreatedAtDesc(Long conversationId);

    Page<Ticket> findByStatusAndAssignedAgentIdIsNullOrderByCreatedAtAsc(TicketStatus status, Pageable pageable);

    @Query("""
            SELECT t.assignedAgentId, COUNT(t.id)
            FROM Ticket t
            WHERE t.assignedAgentId IN :agentIds
              AND t.status IN ('NEW', 'ASSIGNED', 'IN_PROGRESS', 'WAITING_CUSTOMER')
            GROUP BY t.assignedAgentId
            """)
    java.util.List<Object[]> countOpenTicketsByAgentIds(@Param("agentIds") Collection<Long> agentIds);

    Page<Ticket> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    Page<Ticket> findByCustomerIdAndStatusOrderByCreatedAtDesc(Long customerId, TicketStatus status, Pageable pageable);

    @Query("""
            SELECT t FROM Ticket t
            WHERE (:status IS NULL OR t.status = :status)
              AND (:priority IS NULL OR t.priority = :priority)
              AND (:categoryId IS NULL OR t.categoryId = :categoryId)
              AND (:teamId IS NULL OR t.teamId = :teamId)
              AND (:assignedAgentId IS NULL OR t.assignedAgentId = :assignedAgentId)
              AND (:unassignedOnly = false OR t.assignedAgentId IS NULL)
              AND (:keyword IS NULL OR LOWER(t.subject) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(t.ticketCode) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:restrictScope = false OR t.assignedAgentId = :currentUserId
                   OR t.teamId IN :allowedTeamIds
                   OR (t.teamId IS NULL AND t.assignedAgentId IS NULL))
            """)
    Page<Ticket> searchStaffTickets(
            @Param("status") TicketStatus status,
            @Param("priority") TicketPriority priority,
            @Param("categoryId") Long categoryId,
            @Param("teamId") Long teamId,
            @Param("assignedAgentId") Long assignedAgentId,
            @Param("unassignedOnly") boolean unassignedOnly,
            @Param("keyword") String keyword,
            @Param("restrictScope") boolean restrictScope,
            @Param("currentUserId") Long currentUserId,
            @Param("allowedTeamIds") Collection<Long> allowedTeamIds,
            Pageable pageable
    );
}
