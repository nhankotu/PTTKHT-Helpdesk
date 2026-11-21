package com.helpdesk.modules.ticket.entity;

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
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "ticket")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_code", nullable = false, unique = true, length = 30)
    private String ticketCode;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "parent_ticket_id")
    private Long parentTicketId;

    @Column(name = "channel_id", nullable = false)
    private Long channelId;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "team_id")
    private Long teamId;

    @Column(name = "assigned_agent_id")
    private Long assignedAgentId;

    @Column(name = "subject", nullable = false, length = 300)
    private String subject;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 25)
    private TicketStatus status = TicketStatus.NEW;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 15)
    private TicketPriority priority = TicketPriority.NORMAL;

    @Column(name = "resolution_code", length = 50)
    private String resolutionCode;

    @Column(name = "cancel_reason", columnDefinition = "TEXT")
    private String cancelReason;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version = 0;

    protected Ticket() {
    }

    public Ticket(
            String ticketCode,
            Long conversationId,
            Long customerId,
            Long parentTicketId,
            Long channelId,
            Long categoryId,
            String subject,
            String description,
            Instant createdAt
    ) {
        this.ticketCode = ticketCode;
        this.conversationId = conversationId;
        this.customerId = customerId;
        this.parentTicketId = parentTicketId;
        this.channelId = channelId;
        this.categoryId = categoryId;
        this.subject = subject;
        this.description = description;
        this.status = TicketStatus.NEW;
        this.priority = TicketPriority.NORMAL;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = this.createdAt;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getTicketCode() { return ticketCode; }
    public Long getConversationId() { return conversationId; }
    public Long getCustomerId() { return customerId; }
    public Long getParentTicketId() { return parentTicketId; }
    public Long getChannelId() { return channelId; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }
    public Long getAssignedAgentId() { return assignedAgentId; }
    public void setAssignedAgentId(Long assignedAgentId) { this.assignedAgentId = assignedAgentId; }
    public String getSubject() { return subject; }
    public String getDescription() { return description; }
    public TicketStatus getStatus() { return status; }
    public void setStatus(TicketStatus status) { this.status = status; }
    public TicketPriority getPriority() { return priority; }
    public void setPriority(TicketPriority priority) { this.priority = priority; }
    public String getResolutionCode() { return resolutionCode; }
    public void setResolutionCode(String resolutionCode) { this.resolutionCode = resolutionCode; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public int getVersion() { return version; }
}
