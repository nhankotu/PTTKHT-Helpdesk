package com.helpdesk.modules.routing.strategy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Strategy Pattern (UC04): Tách thuật toán chọn Agent khả dụng trong nhóm hỗ trợ.
 */
public interface AgentAssignmentStrategy {

    Optional<Long> selectAgent(
            List<Long> candidateAgentIds,
            Map<Long, Long> openTicketCounts,
            Map<Long, Instant> lastAutoAssignedAt
    );
}
