package com.helpdesk.modules.routing.dto;

import com.helpdesk.modules.routing.entity.AssignmentSource;
import com.helpdesk.modules.routing.entity.ClassificationOutcome;
import com.helpdesk.modules.ticket.entity.TicketPriority;
import com.helpdesk.modules.ticket.entity.TicketStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public final class RoutingDtos {

    private RoutingDtos() {
    }

    public record RoutingRuleDto(
            Long id,
            Long categoryId,
            Long teamId,
            BigDecimal confidenceThreshold,
            boolean autoAssignEnabled,
            boolean isActive
    ) {
    }

    public record RoutingRuleCreateRequest(
            @NotNull Long categoryId,
            @NotNull Long teamId,
            @NotNull @DecimalMin("0.0000") @DecimalMax("1.0000") BigDecimal confidenceThreshold,
            boolean autoAssignEnabled,
            boolean isActive
    ) {
    }

    public record RoutingRuleUpdateRequest(
            Long teamId,
            @DecimalMin("0.0000") @DecimalMax("1.0000") BigDecimal confidenceThreshold,
            Boolean autoAssignEnabled,
            Boolean isActive
    ) {
    }

    public record ClassificationResultDto(
            Long id,
            Long ticketId,
            Long suggestedCategoryId,
            BigDecimal confidence,
            String modelVersion,
            ClassificationOutcome outcome,
            String failureReason,
            Instant createdAt
    ) {
    }

    public record ReviewQueueItemDto(
            Long ticketId,
            String ticketCode,
            String subject,
            TicketPriority priority,
            TicketStatus status,
            int version,
            Instant createdAt,
            ClassificationResultDto latestClassification
    ) {
    }

    public record ManualAssignRequest(
            @NotNull(message = "Bắt buộc cung cấp version để chống ghi đè đồng thời")
            Integer version,

            @NotNull Long categoryId,
            @NotNull Long teamId,
            @NotNull Long agentId,

            @NotBlank(message = "Vui lòng nhập lý do phân công thủ công")
            @Size(max = 1000)
            String reason
    ) {
    }

    public record AssignmentResultDto(
            Long ticketId,
            TicketStatus status,
            Long categoryId,
            Long teamId,
            Long assignedAgentId,
            AssignmentSource assignmentSource,
            int version,
            Instant assignedAt
    ) {
    }
}
