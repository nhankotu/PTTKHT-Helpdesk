package com.helpdesk.modules.knowledge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "ai_citation")
public class AiCitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ai_run_id", nullable = false)
    private Long aiRunId;

    @Column(name = "kb_chunk_id", nullable = false)
    private Long kbChunkId;

    @Column(name = "kb_version_id", nullable = false)
    private Long kbVersionId;

    @Column(name = "relevance_score", nullable = false, precision = 5, scale = 4)
    private BigDecimal relevanceScore;

    @Column(name = "citation_order", nullable = false)
    private int citationOrder;

    protected AiCitation() {
    }

    public AiCitation(Long aiRunId, Long kbChunkId, Long kbVersionId, BigDecimal relevanceScore, int citationOrder) {
        this.aiRunId = aiRunId;
        this.kbChunkId = kbChunkId;
        this.kbVersionId = kbVersionId;
        this.relevanceScore = relevanceScore;
        this.citationOrder = citationOrder;
    }

    public Long getId() { return id; }
    public Long getAiRunId() { return aiRunId; }
    public Long getKbChunkId() { return kbChunkId; }
    public Long getKbVersionId() { return kbVersionId; }
    public BigDecimal getRelevanceScore() { return relevanceScore; }
    public int getCitationOrder() { return citationOrder; }
}
