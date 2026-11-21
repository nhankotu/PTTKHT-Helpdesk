package com.helpdesk.modules.intake.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "conversation")
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "channel_account_id", nullable = false)
    private Long channelAccountId;

    @Column(name = "customer_identity_id")
    private Long customerIdentityId;

    @Column(name = "external_thread_id", nullable = false, length = 255)
    private String externalThreadId;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Conversation() {
    }

    public Conversation(Long channelAccountId, Long customerIdentityId, String externalThreadId, Instant startedAt) {
        this.channelAccountId = channelAccountId;
        this.customerIdentityId = customerIdentityId;
        this.externalThreadId = externalThreadId;
        this.startedAt = startedAt;
        this.updatedAt = startedAt;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (this.startedAt == null) {
            this.startedAt = now;
        }
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getChannelAccountId() { return channelAccountId; }
    public Long getCustomerIdentityId() { return customerIdentityId; }
    public String getExternalThreadId() { return externalThreadId; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
