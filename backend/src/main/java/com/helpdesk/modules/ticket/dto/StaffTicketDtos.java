package com.helpdesk.modules.ticket.dto;

import com.helpdesk.modules.ticket.entity.MessageDeliveryStatus;
import com.helpdesk.modules.ticket.entity.MessageDirection;
import com.helpdesk.modules.ticket.entity.MessageSenderType;
import com.helpdesk.modules.ticket.entity.MessageVisibility;
import com.helpdesk.modules.ticket.entity.TicketEventAction;
import com.helpdesk.modules.ticket.entity.TicketPriority;
import com.helpdesk.modules.ticket.entity.TicketStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Bộ DTO dành cho Không gian Nhân viên (AGENT, SUPERVISOR, ADMIN).
 */
public final class StaffTicketDtos {

    private StaffTicketDtos() {
    }

    public record StaffTicketDetailDto(
            Long id,
            String ticketCode,
            Long conversationId,
            Long parentTicketId,
            String subject,
            String description,
            TicketStatus status,
            TicketPriority priority,
            String resolutionCode,
            String cancelReason,
            Long channelId,
            Long categoryId,
            Long teamId,
            Long assignedAgentId,
            Long customerId,
            List<String> allowedTransitions,
            Instant resolvedAt,
            Instant closedAt,
            Instant createdAt,
            Instant updatedAt,
            int version
    ) {
    }

    public record StaffMessageDto(
            Long id,
            Long ticketId,
            Long senderUserId,
            String senderDisplayName,
            MessageSenderType senderType,
            MessageDirection direction,
            MessageVisibility visibility,
            boolean isAutoAck,
            String body,
            MessageDeliveryStatus deliveryStatus,
            Instant occurredAt
    ) {
    }

    public record StaffMessageCreateRequest(
            @NotNull(message = "visibility bắt buộc (PUBLIC hoặc INTERNAL)")
            MessageVisibility visibility,

            @NotBlank(message = "Nội dung không được để trống")
            @Size(max = 10000, message = "Nội dung tối đa 10000 ký tự")
            String body,

            TicketStatus targetStatus,
            String resolutionCode,
            Integer version
    ) {
    }

    public record StaffMessageCreateResponse(
            StaffMessageDto message,
            TicketStatus ticketStatus,
            int ticketVersion
    ) {
    }

    public record StatusUpdateRequest(
            @NotNull(message = "Bắt buộc cung cấp version để chống ghi đè đồng thời")
            Integer version,

            @NotNull(message = "targetStatus không được để trống")
            TicketStatus targetStatus,

            String resolutionCode,
            String cancelReason
    ) {
    }

    public record MetadataUpdateRequest(
            @NotNull(message = "Bắt buộc cung cấp version để chống ghi đè đồng thời")
            Integer version,

            TicketPriority priority,
            Long categoryId
    ) {
    }

    public record TransferTicketRequest(
            @NotNull(message = "Bắt buộc cung cấp version để chống ghi đè đồng thời")
            Integer version,

            @NotNull(message = "teamId không được để trống")
            Long teamId,

            Long assignedAgentId,

            @NotBlank(message = "Vui lòng nhập lý do chuyển giao")
            @Size(max = 1000)
            String reason
    ) {
    }

    public record TicketEventDto(
            Long id,
            Long ticketId,
            Long actorId,
            TicketEventAction actionType,
            String fieldName,
            String oldValue,
            String newValue,
            String reason,
            Instant createdAt
    ) {
    }
}
