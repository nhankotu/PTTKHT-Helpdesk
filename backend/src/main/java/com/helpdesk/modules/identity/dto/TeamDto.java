package com.helpdesk.modules.identity.dto;

public record TeamDto(
        Long id,
        String code,
        String name,
        Long supervisorId,
        String supervisorName,
        int memberCount,
        boolean isActive
) {
}
