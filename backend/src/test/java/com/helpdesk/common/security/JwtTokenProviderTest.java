package com.helpdesk.common.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String TEST_SECRET = "TestSecretKeyMustBeAtLeast32BytesLongForHS256Algorithm!";

    @Test
    @DisplayName("Sinh và giải mã Access Token giữ nguyên userId, email, roles, teamIds và customerId")
    void generateAndParseAccessToken() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-11-21T16:00:00Z"), ZoneOffset.UTC);
        JwtTokenProvider provider = new JwtTokenProvider(new JwtProperties(TEST_SECRET, 3600, 86400), fixedClock);

        HelpdeskUserPrincipal input = new HelpdeskUserPrincipal(
                12L, "agent.an@helpdesk.local", Set.of("AGENT"), Set.of(2L, 5L), null
        );

        String token = provider.generateAccessToken(input);
        HelpdeskUserPrincipal parsed = provider.parseAccessToken(token);

        assertThat(parsed.userId()).isEqualTo(12L);
        assertThat(parsed.email()).isEqualTo("agent.an@helpdesk.local");
        assertThat(parsed.roles()).containsExactly("AGENT");
        assertThat(parsed.teamIds()).containsExactlyInAnyOrder(2L, 5L);
        assertThat(parsed.customerId()).isNull();
    }

    @Test
    @DisplayName("Từ chối Access Token đã hết hạn dựa trên Clock xác định")
    void rejectExpiredAccessToken() {
        Instant issuedTime = Instant.parse("2026-11-21T16:00:00Z");
        JwtTokenProvider issuer = new JwtTokenProvider(
                new JwtProperties(TEST_SECRET, 60, 86400),
                Clock.fixed(issuedTime, ZoneOffset.UTC)
        );
        String token = issuer.generateAccessToken(
                new HelpdeskUserPrincipal(1L, "u@test.local", Set.of("CUSTOMER"), Set.of(), 10L)
        );

        JwtTokenProvider verifierAfterExpiry = new JwtTokenProvider(
                new JwtProperties(TEST_SECRET, 60, 86400),
                Clock.fixed(issuedTime.plusSeconds(120), ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> verifierAfterExpiry.parseAccessToken(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("Không cho phép dùng Refresh Token thay cho Access Token")
    void rejectRefreshTokenAsAccessToken() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-11-21T16:00:00Z"), ZoneOffset.UTC);
        JwtTokenProvider provider = new JwtTokenProvider(new JwtProperties(TEST_SECRET, 3600, 86400), fixedClock);

        String refreshToken = provider.generateRefreshToken(12L, "agent.an@helpdesk.local");

        assertThat(provider.parseRefreshTokenUserId(refreshToken)).isEqualTo(12L);
        assertThatThrownBy(() -> provider.parseAccessToken(refreshToken))
                .isInstanceOf(JwtException.class);
    }
}
