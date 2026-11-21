package com.helpdesk.modules.routing.entity;

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
@Table(name = "assignment_history")
public class AssignmentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "agent_id", nullable = false)
    private Long agentId;

    @Column(name = "assigned_by")
    private Long assignedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_source", nullable = false, length = 10)
    private AssignmentSource assignmentSource;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    protected AssignmentHistory() {
    }

    public AssignmentHistory(
            Long ticketId,
            Long categoryId,
            Long teamId,
            Long agentId,
            Long assignedBy,
            AssignmentSource assignmentSource,
            String reason,
            Instant assignedAt
    ) {
        this.ticketId = ticketId;
        this.categoryId = categoryId;
        this.teamId = teamId;
        this.agentId = agentId;
        this.assignedBy = assignedBy;
        this.assignmentSource = assignmentSource;
        this.reason = reason;
        this.assignedAt = assignedAt;
    }

    @PrePersist
    void onCreate() {
        if (this.assignedAt == null) {
            this.assignedAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public Long getTicketId() { return ticketId; }
    public Long getCategoryId() { return categoryId; }
    public Long getTeamId() { return teamId; }
    public Long getAgentId() { return agentId; }
    public Long getAssignedBy() { return assignedBy; }
    public AssignmentSource getAssignmentSource() { return assignmentSource; }
    public String getReason() { return reason; }
    public Instant getAssignedAt() { return assignedAt; }
}
