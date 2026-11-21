package com.helpdesk.modules.routing.repository;

import com.helpdesk.modules.routing.entity.AssignmentHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AssignmentHistoryRepository extends JpaRepository<AssignmentHistory, Long> {

    List<AssignmentHistory> findByTicketIdOrderByAssignedAtDesc(Long ticketId);

    @Query("""
            SELECT a.agentId, MAX(a.assignedAt)
            FROM AssignmentHistory a
            WHERE a.agentId IN :agentIds AND a.assignmentSource = 'AUTO'
            GROUP BY a.agentId
            """)
    List<Object[]> findLastAutoAssignedAtByAgentIds(@Param("agentIds") Collection<Long> agentIds);
}
