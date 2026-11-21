package com.helpdesk.modules.identity.dto;

public record CategoryDto(
        Long id,
        String code,
        String name,
        boolean isActive
) {
}
