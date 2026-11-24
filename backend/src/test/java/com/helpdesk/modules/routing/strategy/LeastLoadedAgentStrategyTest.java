package com.helpdesk.modules.routing.strategy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LeastLoadedAgentStrategyTest {

    private final LeastLoadedAgentStrategy strategy = new LeastLoadedAgentStrategy();

    @Test
    @DisplayName("Chọn Agent có ít ticket đang mở nhất")
    void selectsAgentWithFewestOpenTickets() {
        Optional<Long> chosen = strategy.selectAgent(
                List.of(10L, 11L, 12L),
                Map.of(10L, 5L, 11L, 2L, 12L, 4L),
                Map.of()
        );
        assertThat(chosen).contains(11L);
    }

    @Test
    @DisplayName("Khi hòa tải, ưu tiên Agent lâu nhất chưa được tự động giao, hòa tiếp theo ID tăng dần")
    void tieBreaksByOldestAutoAssignmentThenId() {
        Instant t1 = Instant.parse("2026-11-21T10:00:00Z");
        Instant t2 = Instant.parse("2026-11-21T12:00:00Z");

        Optional<Long> chosen = strategy.selectAgent(
                List.of(20L, 15L, 18L),
                Map.of(20L, 2L, 15L, 2L, 18L, 2L),
                Map.of(20L, t2, 15L, t1, 18L, t1)
        );
        assertThat(chosen).contains(15L);
    }

    @Test
    @DisplayName("Trả rỗng khi không có Agent khả dụng")
    void returnsEmptyWhenNoCandidates() {
        assertThat(strategy.selectAgent(List.of(), Map.of(), Map.of())).isEmpty();
    }
}
