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

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ai_run")
public class AiRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "requested_by")
    private Long requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 25)
    private AiTaskType taskType = AiTaskType.RAG_SUGGESTION;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Column(name = "prompt_version", nullable = false, length = 50)
    private String promptVersion;

    @Column(name = "query_text", nullable = false, columnDefinition = "TEXT")
    private String queryText;

    @Column(name = "answer_text", columnDefinition = "TEXT")
    private String answerText;

    @Column(name = "confidence", precision = 5, scale = 4)
    private BigDecimal confidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 25)
    private AiRunOutcome outcome;

    @Enumerated(EnumType.STRING)
    @Column(name = "fallback_reason", length = 50)
    private AiFallbackReason fallbackReason;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AiRun() {
    }

    public AiRun(
            Long ticketId,
            Long requestedBy,
            AiTaskType taskType,
            String modelName,
            String promptVersion,
            String queryText,
            String answerText,
            BigDecimal confidence,
            AiRunOutcome outcome,
            AiFallbackReason fallbackReason,
            Integer latencyMs,
            Instant createdAt
    ) {
        this.ticketId = ticketId;
        this.requestedBy = requestedBy;
        this.taskType = taskType;
        this.modelName = modelName;
        this.promptVersion = promptVersion;
        this.queryText = queryText;
        this.answerText = answerText;
        this.confidence = confidence;
        this.outcome = outcome;
        this.fallbackReason = fallbackReason;
        this.latencyMs = latencyMs;
        this.createdAt = createdAt;
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public Long getTicketId() { return ticketId; }
    public Long getRequestedBy() { return requestedBy; }
    public AiTaskType getTaskType() { return taskType; }
    public String getModelName() { return modelName; }
    public String getPromptVersion() { return promptVersion; }
    public String getQueryText() { return queryText; }
    public String getAnswerText() { return answerText; }
    public BigDecimal getConfidence() { return confidence; }
    public AiRunOutcome getOutcome() { return outcome; }
    public AiFallbackReason getFallbackReason() { return fallbackReason; }
    public Integer getLatencyMs() { return latencyMs; }
    public Instant getCreatedAt() { return createdAt; }
}
