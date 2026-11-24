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
@Table(name = "sla_event")
public class SlaEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_sla_id", nullable = false)
    private Long ticketSlaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 10)
    private SlaEventType eventType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SlaEvent() {
    }

    public SlaEvent(Long ticketSlaId, SlaEventType eventType, Instant createdAt) {
        this.ticketSlaId = ticketSlaId;
        this.eventType = eventType;
        this.createdAt = createdAt;
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public Long getTicketSlaId() { return ticketSlaId; }
    public SlaEventType getEventType() { return eventType; }
    public Instant getCreatedAt() { return createdAt; }
}
