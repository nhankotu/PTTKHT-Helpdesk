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

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "classification_result")
public class ClassificationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "suggested_category_id")
    private Long suggestedCategoryId;

    @Column(name = "confidence", precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "model_version", length = 100)
    private String modelVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 25)
    private ClassificationOutcome outcome;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ClassificationResult() {
    }

    public ClassificationResult(
            Long ticketId,
            Long suggestedCategoryId,
            BigDecimal confidence,
            String modelVersion,
            ClassificationOutcome outcome,
            String failureReason,
            Instant createdAt
    ) {
        this.ticketId = ticketId;
        this.suggestedCategoryId = suggestedCategoryId;
        this.confidence = confidence;
        this.modelVersion = modelVersion;
        this.outcome = outcome;
        this.failureReason = failureReason;
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
    public Long getSuggestedCategoryId() { return suggestedCategoryId; }
    public BigDecimal getConfidence() { return confidence; }
    public String getModelVersion() { return modelVersion; }
    public ClassificationOutcome getOutcome() { return outcome; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
}
