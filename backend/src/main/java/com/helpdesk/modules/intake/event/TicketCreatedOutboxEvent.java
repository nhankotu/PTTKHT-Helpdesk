package com.helpdesk.modules.intake.event;

import java.time.Instant;

/**
 * Sự kiện nghiệp vụ phát ra từ OutboxDispatcherWorker khi một Ticket mới được tạo bền vững.
 * Module SLA (UC05) và Routing (UC04) lắng nghe sự kiện này một cách bất đồng bộ & độc lập.
 */
public record TicketCreatedOutboxEvent(
        Long outboxEventId,
        Long ticketId,
        String ticketCode,
        Instant createdAt
) {
}
