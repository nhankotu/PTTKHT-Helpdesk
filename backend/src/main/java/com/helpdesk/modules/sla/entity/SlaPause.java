package com.helpdesk.modules.sla.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "sla_pause")
public class SlaPause {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_sla_id", nullable = false)
    private Long ticketSlaId;

    @Column(name = "paused_at", nullable = false)
    private Instant pausedAt;

    @Column(name = "resumed_at")
    private Instant resumedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 50)
    private SlaPauseReason reason;

    protected SlaPause() {
    }

    public SlaPause(Long ticketSlaId, Instant pausedAt, Instant resumedAt, SlaPauseReason reason) {
        this.ticketSlaId = ticketSlaId;
        this.pausedAt = pausedAt;
        this.resumedAt = resumedAt;
        this.reason = reason;
    }

    public Long getId() { return id; }
    public Long getTicketSlaId() { return ticketSlaId; }
    public Instant getPausedAt() { return pausedAt; }
    public Instant getResumedAt() { return resumedAt; }
    public void setResumedAt(Instant resumedAt) { this.resumedAt = resumedAt; }
    public SlaPauseReason getReason() { return reason; }
}
