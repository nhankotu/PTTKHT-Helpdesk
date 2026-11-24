package com.helpdesk.modules.ticket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "satisfaction_rating")
public class SatisfactionRating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false, unique = true)
    private Long ticketId;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "score")
    private Byte score;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    protected SatisfactionRating() {
    }

    public SatisfactionRating(Long ticketId, Long customerId, Instant sentAt) {
        this.ticketId = ticketId;
        this.customerId = customerId;
        this.sentAt = sentAt;
    }

    @PrePersist
    void onCreate() {
        if (this.sentAt == null) {
            this.sentAt = Instant.now();
        }
    }

    public void submit(byte score, String comment, Instant submittedAt) {
        this.score = score;
        this.comment = comment;
        this.submittedAt = submittedAt;
    }

    public Long getId() { return id; }
    public Long getTicketId() { return ticketId; }
    public Long getCustomerId() { return customerId; }
    public Byte getScore() { return score; }
    public String getComment() { return comment; }
    public Instant getSentAt() { return sentAt; }
    public Instant getSubmittedAt() { return submittedAt; }
}
