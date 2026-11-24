package com.helpdesk.modules.intake.adapter;

import com.helpdesk.modules.identity.entity.ChannelCode;
import com.helpdesk.modules.intake.dto.IntakeDtos.WebhookInboundRequest;
import org.springframework.stereotype.Component;

@Component
public class SocialChatInboundAdapter implements InboundChannelAdapter {

    @Override
    public boolean supports(ChannelCode channelCode) {
        return channelCode == ChannelCode.LIVE_CHAT
                || channelCode == ChannelCode.FACEBOOK
                || channelCode == ChannelCode.ZALO;
    }

    @Override
    public NormalizedInboundMessage normalize(ChannelCode channelCode, WebhookInboundRequest req) {
        String subject;
        if (req.subject() != null && !req.subject().isBlank()) {
            subject = req.subject().trim();
        } else {
            String preview = req.body().trim();
            subject = "[" + channelCode.name() + "] " + (preview.length() > 80 ? preview.substring(0, 80) + "..." : preview);
        }
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
