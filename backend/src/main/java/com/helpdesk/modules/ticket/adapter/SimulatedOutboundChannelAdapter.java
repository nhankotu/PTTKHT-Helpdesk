package com.helpdesk.modules.ticket.adapter;

import com.helpdesk.modules.ticket.entity.MessageDeliveryStatus;
import com.helpdesk.modules.ticket.entity.Ticket;
import com.helpdesk.modules.ticket.entity.TicketMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Adapter mô phỏng gửi phản hồi ra kênh ngoài (Email/Web/Chat/Social).
 * Hỗ trợ kịch bản test lỗi truyền tải UC03-E1 khi nội dung chứa marker "[SIMULATE_DELIVERY_FAILURE]".
 */
@Component
public class SimulatedOutboundChannelAdapter implements OutboundChannelAdapter {

    private static final Logger log = LoggerFactory.getLogger(SimulatedOutboundChannelAdapter.class);
    public static final String FAILURE_MARKER = "[SIMULATE_DELIVERY_FAILURE]";

    @Override
    public MessageDeliveryStatus deliver(Ticket ticket, TicketMessage message) {
        if (message.getBody() != null && message.getBody().contains(FAILURE_MARKER)) {
            log.warn("Simulated outbound channel delivery FAILED for ticket={} message={}",
                    ticket.getTicketCode(), message.getId());
            return MessageDeliveryStatus.FAILED;
        }
        log.info("Outbound message delivered via channelId={} for ticket={} message={}",
                ticket.getChannelId(), ticket.getTicketCode(), message.getId());
        return MessageDeliveryStatus.SENT;
    }
}
