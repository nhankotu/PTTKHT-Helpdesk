package com.helpdesk.modules.sla.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "escalation_action")
public class EscalationAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sla_event_id", nullable = false)
    private Long slaEventId;

    @Column(name = "handled_by", nullable = false)
    private Long handledBy;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "handled_at", nullable = false, updatable = false)
    private Instant handledAt;

    protected EscalationAction() {
    }

    public EscalationAction(Long slaEventId, Long handledBy, String action, String note, Instant handledAt) {
        this.slaEventId = slaEventId;
        this.handledBy = handledBy;
        this.action = action;
        this.note = note;
        this.handledAt = handledAt;
    }

    @PrePersist
    void onCreate() {
        if (this.handledAt == null) {
            this.handledAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public Long getSlaEventId() { return slaEventId; }
    public Long getHandledBy() { return handledBy; }
    public String getAction() { return action; }
    public String getNote() { return note; }
    public Instant getHandledAt() { return handledAt; }
}
