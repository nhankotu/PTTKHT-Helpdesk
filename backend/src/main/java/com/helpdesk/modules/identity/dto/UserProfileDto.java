package com.helpdesk.modules.identity.dto;

import java.util.Set;

public record UserProfileDto(
        Long id,
        String fullName,
        String email,
        String phone,
        Set<String> roles,
        Set<Long> teamIds,
        Long customerId,
        boolean isActive,
        boolean isAvailable
) {
}
