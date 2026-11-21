package com.helpdesk.modules.intake.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "customer_identity")
public class CustomerIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "channel_account_id", nullable = false)
    private Long channelAccountId;

    @Column(name = "external_user_id", nullable = false, length = 255)
    private String externalUserId;

    @Column(name = "display_name", length = 150)
    private String displayName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CustomerIdentity() {
    }

    public CustomerIdentity(Long customerId, Long channelAccountId, String externalUserId, String displayName) {
        this.customerId = customerId;
        this.channelAccountId = channelAccountId;
        this.externalUserId = externalUserId;
        this.displayName = displayName;
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public Long getCustomerId() { return customerId; }
    public Long getChannelAccountId() { return channelAccountId; }
    public String getExternalUserId() { return externalUserId; }
    public String getDisplayName() { return displayName; }
    public Instant getCreatedAt() { return createdAt; }
}
