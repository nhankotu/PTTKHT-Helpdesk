package com.helpdesk.modules.identity.dto;

import jakarta.validation.constraints.NotNull;

public record AvailabilityRequest(
        @NotNull(message = "Trạng thái sẵn sàng không được để trống")
        Boolean isAvailable
) {
}
