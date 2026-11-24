package com.helpdesk.modules.intake.dto;

import com.helpdesk.modules.intake.entity.InboundProcessStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class IntakeDtos {

    private IntakeDtos() {
    }

    public record ContactInfo(
            @Size(max = 150) String fullName,
            @Email(message = "Định dạng email liên hệ không hợp lệ") @Size(max = 255) String email,
            @Size(max = 30) String phone
    ) {
    }

    public record WebIntakeRequest(
            @NotBlank(message = "Tiêu đề không được để trống")
            @Size(max = 300, message = "Tiêu đề tối đa 300 ký tự")
            String subject,

            @NotBlank(message = "Nội dung yêu cầu không được để trống")
            @Size(max = 10000, message = "Nội dung tối đa 10000 ký tự")
            String description,

            Long categoryId,

            @Valid ContactInfo contact,

            List<Long> attachmentIds
    ) {
    }

    public record WebIntakeResponse(
            Long requestId,
            String idempotencyKey,
            InboundProcessStatus processStatus,
            Long ticketId,
            String ticketCode,
            boolean duplicate,
            Instant receivedAt
    ) {
    }

    public record WebhookSender(
            @NotBlank(message = "sender.externalUserId không được để trống")
            @Size(max = 255)
            String externalUserId,

            @Size(max = 150) String displayName,
            @Size(max = 255) String email,
            @Size(max = 30) String phone
    ) {
    }

    public record WebhookInboundRequest(
            @NotBlank(message = "channelAccountCode không được để trống")
            String channelAccountCode,

            @NotBlank(message = "externalEventId không được để trống")
            @Size(max = 255)
            String externalEventId,

            @NotBlank(message = "externalThreadId không được để trống")
            @Size(max = 255)
            String externalThreadId,

            @Size(max = 255)
            String externalMessageId,

            @NotNull(message = "sender không được để trống")
            @Valid
            WebhookSender sender,

            @Size(max = 300)
            String subject,

            @NotBlank(message = "body không được để trống")
            @Size(max = 10000)
            String body,

            Instant occurredAt
    ) {
    }

    public record WebhookInboundResponse(
            Long inboundEventId,
            String externalEventId,
            InboundProcessStatus processStatus,
            boolean duplicate,
            String action,
            Long ticketId,
            String ticketCode,
            Long messageId
    ) {
    }

    public record InboundEventSummaryDto(
            Long id,
            Long channelAccountId,
            String externalEventId,
            String idempotencyKey,
            InboundProcessStatus processStatus,
            Long ticketId,
            Long messageId,
            String errorCode,
            Instant receivedAt,
            Instant processedAt
    ) {
    }
}
