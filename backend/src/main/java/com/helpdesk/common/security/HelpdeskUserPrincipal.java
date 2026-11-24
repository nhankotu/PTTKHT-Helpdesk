package com.helpdesk.common.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public record HelpdeskUserPrincipal(
        Long userId,
        String email,
        Set<String> roles,
        Set<Long> teamIds,
        Long customerId
) {
    public HelpdeskUserPrincipal {
        roles = roles == null ? Collections.emptySet() : Set.copyOf(roles);
        teamIds = teamIds == null ? Collections.emptySet() : Set.copyOf(teamIds);
    }

    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean hasRole(String roleCode) {
        return roles.contains(roleCode);
    }

    public boolean isInTeam(Long teamId) {
        return teamId != null && teamIds.contains(teamId);
    }
}
