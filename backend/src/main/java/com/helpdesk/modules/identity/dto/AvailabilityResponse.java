package com.helpdesk.modules.identity.dto;

import java.time.Instant;

public record AvailabilityResponse(
        Long userId,
        boolean isAvailable,
        Instant updatedAt
) {
}
