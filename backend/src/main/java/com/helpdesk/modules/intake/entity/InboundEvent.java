package com.helpdesk.modules.intake.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "inbound_event")
public class InboundEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "channel_account_id", nullable = false)
    private Long channelAccountId;

    @Column(name = "external_event_id", nullable = false, length = 255)
    private String externalEventId;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "payload_hash", length = 64)
    private String payloadHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_redacted", columnDefinition = "json")
    private String payloadRedacted;

    @Enumerated(EnumType.STRING)
    @Column(name = "process_status", nullable = false, length = 20)
    private InboundProcessStatus processStatus = InboundProcessStatus.RECEIVED;

    @Column(name = "ticket_id")
    private Long ticketId;

    @Column(name = "message_id")
    private Long messageId;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected InboundEvent() {
    }

    public InboundEvent(
            Long channelAccountId,
            String externalEventId,
            String idempotencyKey,
            String payloadHash,
            String payloadRedacted,
            Instant receivedAt
    ) {
        this.channelAccountId = channelAccountId;
        this.externalEventId = externalEventId;
        this.idempotencyKey = idempotencyKey;
        this.payloadHash = payloadHash;
        this.payloadRedacted = payloadRedacted;
        this.processStatus = InboundProcessStatus.RECEIVED;
        this.receivedAt = receivedAt;
    }

    @PrePersist
    void onCreate() {
        if (this.receivedAt == null) {
            this.receivedAt = Instant.now();
        }
    }

    public void markProcessed(Long ticketId, Long messageId, Instant processedAt) {
        this.ticketId = ticketId;
        this.messageId = messageId;
        this.processStatus = InboundProcessStatus.PROCESSED;
        this.processedAt = processedAt;
    }

    public void markFailed(String errorCode, Instant processedAt) {
        this.errorCode = errorCode;
        this.processStatus = InboundProcessStatus.FAILED;
        this.processedAt = processedAt;
    }

    public Long getId() { return id; }
    public Long getChannelAccountId() { return channelAccountId; }
    public String getExternalEventId() { return externalEventId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getPayloadHash() { return payloadHash; }
    public String getPayloadRedacted() { return payloadRedacted; }
    public InboundProcessStatus getProcessStatus() { return processStatus; }
    public Long getTicketId() { return ticketId; }
    public Long getMessageId() { return messageId; }
    public String getErrorCode() { return errorCode; }
    public Instant getReceivedAt() { return receivedAt; }
    public Instant getProcessedAt() { return processedAt; }
}
