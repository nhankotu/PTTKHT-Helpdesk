package com.helpdesk.modules.intake.adapter;

import com.helpdesk.modules.identity.entity.ChannelCode;
import com.helpdesk.modules.intake.dto.IntakeDtos.WebhookInboundRequest;

/**
 * Adapter Pattern (Inbound): Chuẩn hóa payload từ từng kênh ngoài (Email, Live Chat, Facebook, Zalo)
 * về mô hình chung NormalizedInboundMessage.
 */
public interface InboundChannelAdapter {

    boolean supports(ChannelCode channelCode);

    NormalizedInboundMessage normalize(ChannelCode channelCode, WebhookInboundRequest request);
}
