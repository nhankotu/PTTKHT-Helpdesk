package com.helpdesk.modules.intake.adapter;

import com.helpdesk.modules.identity.entity.ChannelCode;
import com.helpdesk.modules.intake.dto.IntakeDtos.WebhookInboundRequest;
import org.springframework.stereotype.Component;

@Component
public class EmailInboundAdapter implements InboundChannelAdapter {

    @Override
    public boolean supports(ChannelCode channelCode) {
        return channelCode == ChannelCode.EMAIL;
    }

    @Override
    public NormalizedInboundMessage normalize(ChannelCode channelCode, WebhookInboundRequest req) {
        String subject = (req.subject() != null && !req.subject().isBlank())
                ? req.subject().trim()
                : "(Không có tiêu đề từ Email)";
        return new NormalizedInboundMessage(
                channelCode,
                req.channelAccountCode().trim(),
                req.externalEventId().trim(),
                req.externalThreadId().trim(),
                req.externalMessageId() != null ? req.externalMessageId().trim() : req.externalEventId().trim(),
                req.sender().externalUserId().trim(),
                req.sender().displayName(),
                req.sender().email(),
                req.sender().phone(),
                null,
                subject,
                req.body().trim(),
                req.occurredAt()
        );
    }
}
