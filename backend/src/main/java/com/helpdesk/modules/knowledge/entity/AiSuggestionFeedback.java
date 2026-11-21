package com.helpdesk.modules.knowledge.entity;

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
@Table(name = "ai_suggestion_feedback")
public class AiSuggestionFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ai_run_id", nullable = false, unique = true)
    private Long aiRunId;

    @Column(name = "agent_id", nullable = false)
    private Long agentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 25)
    private AiFeedbackDecision decision;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AiSuggestionFeedback() {
    }

    public AiSuggestionFeedback(Long aiRunId, Long agentId, AiFeedbackDecision decision, String note, Instant createdAt) {
        this.aiRunId = aiRunId;
        this.agentId = agentId;
        this.decision = decision;
        this.note = note;
        this.createdAt = createdAt;
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public Long getAiRunId() { return aiRunId; }
    public Long getAgentId() { return agentId; }
    public AiFeedbackDecision getDecision() { return decision; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }
}
