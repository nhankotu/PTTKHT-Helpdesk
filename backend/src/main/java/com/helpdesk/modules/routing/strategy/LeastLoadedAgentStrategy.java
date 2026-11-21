package com.helpdesk.modules.routing.strategy;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Chiến lược cân bằng tải mặc định (UC04):
 * 1. Chọn Agent có ít ticket đang mở nhất.
 * 2. Nếu hòa, chọn Agent lâu nhất chưa được tự động giao (null = chưa từng được giao -> ưu tiên nhất).
 * 3. Nếu hòa tiếp, chọn theo app_user.id tăng dần.
 */
@Component
public class LeastLoadedAgentStrategy implements AgentAssignmentStrategy {

    @Override
    public Optional<Long> selectAgent(
            List<Long> candidateAgentIds,
            Map<Long, Long> openTicketCounts,
            Map<Long, Instant> lastAutoAssignedAt
    ) {
        if (candidateAgentIds == null || candidateAgentIds.isEmpty()) {
            return Optional.empty();
        }
        return candidateAgentIds.stream()
                .min(Comparator
                        .comparingLong((Long id) -> openTicketCounts.getOrDefault(id, 0L))
                        .thenComparing(id -> lastAutoAssignedAt.getOrDefault(id, Instant.EPOCH))
                        .thenComparingLong(Long::longValue));
    }
}
