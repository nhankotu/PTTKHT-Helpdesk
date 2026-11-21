package com.helpdesk.modules.ticket.dto;

import com.helpdesk.modules.ticket.entity.MessageDirection;
import com.helpdesk.modules.ticket.entity.MessageSenderType;
import com.helpdesk.modules.ticket.entity.TicketStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Bộ DTO dành riêng cho Cổng Khách hàng (CUSTOMER).
 * HARD INVARIANT: Tuyệt đối không chứa bất kỳ trường dữ liệu nội bộ (INTERNAL) nào.
 */
public final class CustomerTicketDtos {

    private CustomerTicketDtos() {
    }

    public record CsatSummaryDto(
            boolean eligible,
            boolean submitted,
            Integer score,
            String comment,
            Instant submittedAt
    ) {
    }

    public record CustomerTicketDetailDto(
            Long id,
            String ticketCode,
            String subject,
            String description,
            TicketStatus status,
            Long channelId,
            Long categoryId,
            String assignedAgentName,
            String resolutionCode,
            Instant resolvedAt,
            Instant closedAt,
            Instant createdAt,
            Instant updatedAt,
            int version,
            CsatSummaryDto csat
    ) {
    }

    public record CustomerMessageDto(
            Long id,
            MessageSenderType senderType,
            String senderDisplayName,
            MessageDirection direction,
            boolean isAutoAck,
            String body,
            Instant occurredAt,
            List<Object> citations
    ) {
    }

    public record CustomerReplyRequest(
            @NotBlank(message = "Nội dung tin nhắn không được để trống")
            @Size(max = 10000, message = "Nội dung tối đa 10000 ký tự")
            String body
    ) {
    }

    public record CustomerCloseRequest(
            @NotNull(message = "Bắt buộc cung cấp version hiện tại của ticket")
            Integer version
    ) {
    }

    public record CustomerReopenRequest(
            @NotNull(message = "Bắt buộc cung cấp version hiện tại của ticket")
            Integer version,

            @NotBlank(message = "Vui lòng nhập lý do mở lại ticket")
            @Size(min = 5, max = 1000, message = "Lý do mở lại phải từ 5 đến 1000 ký tự")
            String reason
    ) {
    }

    public record CsatSubmitRequest(
            @NotNull(message = "Điểm đánh giá không được để trống")
            @Min(value = 1, message = "Điểm đánh giá từ 1 đến 5")
            @Max(value = 5, message = "Điểm đánh giá từ 1 đến 5")
            Integer score,

            @Size(max = 2000, message = "Nhận xét tối đa 2000 ký tự")
            String comment
    ) {
    }

    public record CsatResponse(
            Long ticketId,
            int score,
            String comment,
            Instant submittedAt
    ) {
    }
}
