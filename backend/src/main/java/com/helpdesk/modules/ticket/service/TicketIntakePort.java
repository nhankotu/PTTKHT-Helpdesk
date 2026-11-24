package com.helpdesk.modules.ticket.service;

import java.time.Instant;

/**
 * Port giao tiếp giữa module intake (UC01-UC02) và module ticket (UC03).
 * Intake gọi port này trong cùng transaction để tạo ticket mới hoặc nối tin nhắn vào ticket đang mở.
 */
public interface TicketIntakePort {

    record IntakeTicketCommand(
            Long conversationId,
            Long customerId,
            Long channelId,
            Long categoryId,
            String subject,
            String body,
            String externalMessageId,
            Instant occurredAt
    ) {
    }

    record IntakeTicketResult(
            Long ticketId,
            String ticketCode,
            Long messageId,
            boolean createdNewTicket
    ) {
    }

    IntakeTicketResult ingestInboundMessage(IntakeTicketCommand command);
}
