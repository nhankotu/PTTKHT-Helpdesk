package com.helpdesk.modules.ticket.service;

import com.helpdesk.modules.ticket.entity.TicketStatus;

import java.util.List;

/**
 * Port chỉ cung cấp nội dung PUBLIC của ticket cho AI RAG Pipeline (UC07).
 * HARD INVARIANT: Tuyệt đối không bao giờ đọc hay trả tin nhắn visibility = INTERNAL.
 */
public interface TicketPublicContextPort {

    record TicketPublicContext(
            Long ticketId,
            String subject,
            TicketStatus status,
            List<String> publicMessageBodies
    ) {
    }

    TicketPublicContext requirePublicContext(Long ticketId);
}
