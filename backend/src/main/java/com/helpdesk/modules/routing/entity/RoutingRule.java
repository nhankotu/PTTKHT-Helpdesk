package com.helpdesk.modules.routing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "routing_rule")
public class RoutingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "confidence_threshold", nullable = false, precision = 5, scale = 4)
    private BigDecimal confidenceThreshold;

    @Column(name = "auto_assign_enabled", nullable = false)
    private boolean autoAssignEnabled = true;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected RoutingRule() {
    }

    public RoutingRule(Long categoryId, Long teamId, BigDecimal confidenceThreshold, boolean autoAssignEnabled, boolean active) {
        this.categoryId = categoryId;
        this.teamId = teamId;
        this.confidenceThreshold = confidenceThreshold;
        this.autoAssignEnabled = autoAssignEnabled;
        this.active = active;
    }

    public Long getId() { return id; }
    public Long getCategoryId() { return categoryId; }
    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }
    public BigDecimal getConfidenceThreshold() { return confidenceThreshold; }
    public void setConfidenceThreshold(BigDecimal confidenceThreshold) { this.confidenceThreshold = confidenceThreshold; }
    public boolean isAutoAssignEnabled() { return autoAssignEnabled; }
    public void setAutoAssignEnabled(boolean autoAssignEnabled) { this.autoAssignEnabled = autoAssignEnabled; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
