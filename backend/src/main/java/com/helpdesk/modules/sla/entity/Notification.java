package com.helpdesk.modules.sla.entity;

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
@Table(name = "notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "sla_event_id")
    private Long slaEventId;

    @Column(name = "assignment_history_id")
    private Long assignmentHistoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 10)
    private NotificationDeliveryStatus deliveryStatus = NotificationDeliveryStatus.SENT;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {
    }

    public static Notification forSlaEvent(Long recipientId, Long ticketId, Long slaEventId, Instant now) {
        Notification n = new Notification();
        n.recipientId = recipientId;
        n.ticketId = ticketId;
        n.slaEventId = slaEventId;
        n.deliveryStatus = NotificationDeliveryStatus.SENT;
        n.createdAt = now;
        n.sentAt = now;
        return n;
    }

    public static Notification forAssignment(Long recipientId, Long ticketId, Long assignmentHistoryId, Instant now) {
        Notification n = new Notification();
        n.recipientId = recipientId;
        n.ticketId = ticketId;
        n.assignmentHistoryId = assignmentHistoryId;
        n.deliveryStatus = NotificationDeliveryStatus.SENT;
        n.createdAt = now;
        n.sentAt = now;
        return n;
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public void markRead(Instant now) {
        if (this.readAt == null) {
            this.readAt = now;
        }
    }

    public Long getId() { return id; }
    public Long getRecipientId() { return recipientId; }
    public Long getTicketId() { return ticketId; }
    public Long getSlaEventId() { return slaEventId; }
    public Long getAssignmentHistoryId() { return assignmentHistoryId; }
    public NotificationDeliveryStatus getDeliveryStatus() { return deliveryStatus; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSentAt() { return sentAt; }
    public Instant getReadAt() { return readAt; }
}
