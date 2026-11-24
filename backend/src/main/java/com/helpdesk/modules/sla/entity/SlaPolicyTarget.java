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

@Entity
@Table(name = "sla_policy_target")
public class SlaPolicyTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric", nullable = false, length = 20)
    private SlaMetric metric;

    @Column(name = "target_seconds", nullable = false)
    private int targetSeconds;

    @Column(name = "warning_ratio", nullable = false, precision = 5, scale = 4)
    private BigDecimal warningRatio;

    protected SlaPolicyTarget() {
    }

    public SlaPolicyTarget(Long policyId, SlaMetric metric, int targetSeconds, BigDecimal warningRatio) {
        this.policyId = policyId;
        this.metric = metric;
        this.targetSeconds = targetSeconds;
        this.warningRatio = warningRatio;
    }

    public Long getId() { return id; }
    public Long getPolicyId() { return policyId; }
    public SlaMetric getMetric() { return metric; }
    public int getTargetSeconds() { return targetSeconds; }
    public BigDecimal getWarningRatio() { return warningRatio; }
}
