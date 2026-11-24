package com.helpdesk.modules.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "channel")
public class Channel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false, unique = true, length = 30)
    private ChannelCode code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected Channel() {
    }

    public Long getId() {
        return id;
    }

    public ChannelCode getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }
}
