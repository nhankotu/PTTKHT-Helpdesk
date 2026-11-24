package com.helpdesk.modules.ticket.adapter;

import com.helpdesk.modules.ticket.entity.MessageDeliveryStatus;
import com.helpdesk.modules.ticket.entity.Ticket;
import com.helpdesk.modules.ticket.entity.TicketMessage;

/**
 * Adapter Pattern (Outbound): Đẩy tin nhắn phản hồi công khai (PUBLIC) ra kênh ngoài.
 */
public interface OutboundChannelAdapter {

    MessageDeliveryStatus deliver(Ticket ticket, TicketMessage message);
}
