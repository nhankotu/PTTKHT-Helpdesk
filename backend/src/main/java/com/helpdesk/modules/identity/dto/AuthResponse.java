package com.helpdesk.modules.identity.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        UserProfileDto user
) {
}
