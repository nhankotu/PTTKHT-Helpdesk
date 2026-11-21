package com.helpdesk.modules.intake.adapter;

import com.helpdesk.modules.identity.entity.ChannelCode;

import java.time.Instant;

/**
 * Mô hình dữ liệu chuẩn hóa chung (NormalizedInboundMessage) từ mọi kênh đa kênh (Adapter Pattern).
 */
public record NormalizedInboundMessage(
        ChannelCode channelCode,
        String channelAccountCode,
        String externalEventId,
        String externalThreadId,
        String externalMessageId,
        String externalUserId,
        String senderName,
        String senderEmail,
        String senderPhone,
        Long categoryId,
        String subject,
        String body,
        Instant occurredAt
) {
}
