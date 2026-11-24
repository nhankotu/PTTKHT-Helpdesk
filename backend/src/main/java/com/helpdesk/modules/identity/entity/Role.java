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
@Table(name = "role")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false, unique = true, length = 30)
    private RoleCode code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    protected Role() {
    }

    public Role(RoleCode code, String name) {
        this.code = code;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public RoleCode getCode() {
        return code;
    }

    public String getName() {
        return name;
    }
}
