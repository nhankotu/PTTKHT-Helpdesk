package com.helpdesk.modules.knowledge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "kb_chunk")
public class KbChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "heading", length = 250)
    private String heading;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "token_count", nullable = false)
    private int tokenCount;

    @Column(name = "embedding_ref", length = 255)
    private String embeddingRef;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "indexed_at", nullable = false, updatable = false)
    private Instant indexedAt;

    protected KbChunk() {
    }

    public KbChunk(Long versionId, int chunkIndex, String heading, String content, int tokenCount, Instant indexedAt) {
        this.versionId = versionId;
        this.chunkIndex = chunkIndex;
        this.heading = heading;
        this.content = content;
        this.tokenCount = Math.max(1, tokenCount);
        this.active = true;
        this.indexedAt = indexedAt;
    }

    @PrePersist
    void onCreate() {
        if (this.indexedAt == null) {
            this.indexedAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public Long getVersionId() { return versionId; }
    public int getChunkIndex() { return chunkIndex; }
    public String getHeading() { return heading; }
    public String getContent() { return content; }
    public int getTokenCount() { return tokenCount; }
    public String getEmbeddingRef() { return embeddingRef; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getIndexedAt() { return indexedAt; }
}
