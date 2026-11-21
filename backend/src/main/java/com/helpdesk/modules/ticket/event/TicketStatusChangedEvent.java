package com.helpdesk.modules.ticket.event;

import com.helpdesk.modules.ticket.entity.TicketStatus;

import java.time.Instant;

/**
 * Domain Event nội bộ phát ra khi trạng thái ticket thay đổi.
 * Module SLA (UC05) lắng nghe sự kiện này để pause/resume/complete/cancel RESOLUTION SLA.
 */
public record TicketStatusChangedEvent(
        Long ticketId,
        TicketStatus previousStatus,
        TicketStatus newStatus,
        Instant occurredAt
) {
}
