package com.helpdesk.modules.ticket.event;

import com.helpdesk.modules.ticket.entity.MessageSenderType;

import java.time.Instant;

/**
 * Domain Event nội bộ phát ra CHỈ KHI phản hồi công khai hữu ích (PUBLIC, isAutoAck=false)
 * đã được gửi thành công (SENT/DELIVERED) tới khách hàng.
 * Module SLA (UC05) lắng nghe để hoàn tất FIRST_RESPONSE SLA.
 */
public record TicketPublicReplySentEvent(
        Long ticketId,
        Long messageId,
        MessageSenderType senderType,
        Instant sentAt
) {
}
