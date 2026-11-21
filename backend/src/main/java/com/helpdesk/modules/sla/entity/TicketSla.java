package com.helpdesk.modules.sla.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ticket_sla")
public class TicketSla {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "policy_target_id", nullable = false)
    private Long policyTargetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric", nullable = false, length = 20)
    private SlaMetric metric;

    @Column(name = "target_seconds", nullable = false)
    private int targetSeconds;

    @Column(name = "warning_ratio", nullable = false, precision = 5, scale = 4)
    private BigDecimal warningRatio;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "deadline_at", nullable = false)
    private Instant deadlineAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "tracking_status", nullable = false, length = 15)
    private SlaTrackingStatus trackingStatus = SlaTrackingStatus.RUNNING;

    @Column(name = "breached_at")
    private Instant breachedAt;

    protected TicketSla() {
    }

    public TicketSla(
            Long ticketId,
            Long policyTargetId,
            SlaMetric metric,
            int targetSeconds,
            BigDecimal warningRatio,
            Instant startedAt,
            Instant deadlineAt
    ) {
        this.ticketId = ticketId;
        this.policyTargetId = policyTargetId;
        this.metric = metric;
        this.targetSeconds = targetSeconds;
        this.warningRatio = warningRatio;
        this.startedAt = startedAt;
        this.deadlineAt = deadlineAt;
        this.trackingStatus = SlaTrackingStatus.RUNNING;
    }

    public Long getId() { return id; }
    public Long getTicketId() { return ticketId; }
    public Long getPolicyTargetId() { return policyTargetId; }
    public SlaMetric getMetric() { return metric; }
    public int getTargetSeconds() { return targetSeconds; }
    public BigDecimal getWarningRatio() { return warningRatio; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getDeadlineAt() { return deadlineAt; }
    public void setDeadlineAt(Instant deadlineAt) { this.deadlineAt = deadlineAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public SlaTrackingStatus getTrackingStatus() { return trackingStatus; }
    public void setTrackingStatus(SlaTrackingStatus trackingStatus) { this.trackingStatus = trackingStatus; }
    public Instant getBreachedAt() { return breachedAt; }
    public void setBreachedAt(Instant breachedAt) { this.breachedAt = breachedAt; }
}
