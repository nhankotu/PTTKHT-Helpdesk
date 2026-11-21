package com.helpdesk.modules.sla.dto;

import com.helpdesk.modules.sla.entity.NotificationDeliveryStatus;
import com.helpdesk.modules.sla.entity.SlaEventType;
import com.helpdesk.modules.sla.entity.SlaMetric;
import com.helpdesk.modules.sla.entity.SlaPauseReason;
import com.helpdesk.modules.sla.entity.SlaTrackingStatus;
import com.helpdesk.modules.ticket.entity.TicketPriority;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class SlaDtos {

    private SlaDtos() {
    }

    public record SlaTargetDto(
            Long id,
            SlaMetric metric,
            int targetSeconds,
            BigDecimal warningRatio
    ) {
    }

    public record SlaPolicyDto(
            Long id,
            String name,
            TicketPriority priority,
            int version,
            boolean isActive,
            Instant createdAt,
            List<SlaTargetDto> targets
    ) {
    }

    public record SlaTargetCreateRequest(
            @NotNull SlaMetric metric,
            @Min(value = 60, message = "targetSeconds tối thiểu 60 giây") int targetSeconds,
            @NotNull @DecimalMin("0.0100") @DecimalMax("0.9900") BigDecimal warningRatio
    ) {
    }

    public record SlaPolicyCreateRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull TicketPriority priority,
            @NotEmpty @Valid List<SlaTargetCreateRequest> targets
    ) {
    }

    public record SlaPauseDto(
            Long id,
            Instant pausedAt,
            Instant resumedAt,
            SlaPauseReason reason
    ) {
    }

    public record EscalationActionDto(
            Long id,
            Long slaEventId,
            Long handledBy,
            String handledByName,
            String action,
            String note,
            Instant handledAt
    ) {
    }

    public record SlaEventDto(
            Long id,
            Long ticketSlaId,
            Long ticketId,
            SlaMetric metric,
            SlaEventType eventType,
            Instant createdAt,
            List<EscalationActionDto> actions
    ) {
    }

    public record TicketSlaDetailDto(
            Long id,
            Long ticketId,
            SlaMetric metric,
            int targetSeconds,
            BigDecimal warningRatio,
            Instant startedAt,
            Instant deadlineAt,
            Instant completedAt,
            SlaTrackingStatus trackingStatus,
            Instant breachedAt,
            List<SlaPauseDto> pauses,
            List<SlaEventDto> events
    ) {
    }

    public record EscalationActionCreateRequest(
            @NotBlank(message = "action không được để trống") @Size(max = 100) String action,
            @Size(max = 2000) String note
    ) {
    }

    public record NotificationDto(
            Long id,
            Long recipientId,
            Long ticketId,
            Long slaEventId,
            Long assignmentHistoryId,
            NotificationDeliveryStatus deliveryStatus,
            Instant createdAt,
            Instant sentAt,
            Instant readAt
    ) {
    }
}
