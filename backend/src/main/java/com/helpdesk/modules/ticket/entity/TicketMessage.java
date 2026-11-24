package com.helpdesk.modules.ticket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "ticket_message")
public class TicketMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "ticket_id")
    private Long ticketId;

    @Column(name = "sender_user_id")
    private Long senderUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_type", nullable = false, length = 20)
    private MessageSenderType senderType;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 10)
    private MessageDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 15)
    private MessageVisibility visibility = MessageVisibility.PUBLIC;

    @Column(name = "is_auto_ack", nullable = false)
    private boolean autoAck = false;

    @Column(name = "external_message_id", length = 255)
    private String externalMessageId;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 20)
    private MessageDeliveryStatus deliveryStatus = MessageDeliveryStatus.SENT;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TicketMessage() {
    }

    public TicketMessage(
            Long conversationId,
            Long ticketId,
            Long senderUserId,
            MessageSenderType senderType,
            MessageDirection direction,
            MessageVisibility visibility,
            boolean autoAck,
            String externalMessageId,
            String body,
            MessageDeliveryStatus deliveryStatus,
            Instant occurredAt
    ) {
        this.conversationId = conversationId;
        this.ticketId = ticketId;
        this.senderUserId = senderUserId;
        this.senderType = senderType;
        this.direction = direction;
        this.visibility = visibility;
        this.autoAck = autoAck;
        this.externalMessageId = externalMessageId;
        this.body = body;
        this.deliveryStatus = deliveryStatus;
        this.occurredAt = occurredAt;
        this.createdAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (this.occurredAt == null) {
            this.occurredAt = now;
        }
        if (this.createdAt == null) {
            this.createdAt = now;
        }
    }

    public Long getId() { return id; }
    public Long getConversationId() { return conversationId; }
    public Long getTicketId() { return ticketId; }
    public Long getSenderUserId() { return senderUserId; }
    public MessageSenderType getSenderType() { return senderType; }
    public MessageDirection getDirection() { return direction; }
    public MessageVisibility getVisibility() { return visibility; }
    public boolean isAutoAck() { return autoAck; }
    public String getExternalMessageId() { return externalMessageId; }
    public String getBody() { return body; }
    public MessageDeliveryStatus getDeliveryStatus() { return deliveryStatus; }
    public void setDeliveryStatus(MessageDeliveryStatus deliveryStatus) { this.deliveryStatus = deliveryStatus; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getCreatedAt() { return createdAt; }
}
