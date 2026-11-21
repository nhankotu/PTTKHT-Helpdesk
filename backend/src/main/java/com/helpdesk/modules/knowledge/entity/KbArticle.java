package com.helpdesk.modules.knowledge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "kb_article")
public class KbArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "article_code", nullable = false, unique = true, length = 30)
    private String articleCode;

    @Column(name = "category_id")
    private Long categoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_scope", nullable = false, length = 20)
    private KbAccessScope accessScope = KbAccessScope.PUBLIC;

    @Column(name = "allow_auto_reply", nullable = false)
    private boolean allowAutoReply = false;

    @Column(name = "current_published_version_id")
    private Long currentPublishedVersionId;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected KbArticle() {
    }

    public KbArticle(String articleCode, Long categoryId, KbAccessScope accessScope, boolean allowAutoReply, Long createdBy, Instant now) {
        this.articleCode = articleCode;
        this.categoryId = categoryId;
        this.accessScope = accessScope;
        this.allowAutoReply = allowAutoReply;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getArticleCode() { return articleCode; }
    public Long getCategoryId() { return categoryId; }
    public KbAccessScope getAccessScope() { return accessScope; }
    public boolean isAllowAutoReply() { return allowAutoReply; }
    public Long getCurrentPublishedVersionId() { return currentPublishedVersionId; }
    public void setCurrentPublishedVersionId(Long currentPublishedVersionId) { this.currentPublishedVersionId = currentPublishedVersionId; }
    public Long getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
