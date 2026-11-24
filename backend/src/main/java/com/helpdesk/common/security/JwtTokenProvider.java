package com.helpdesk.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class JwtTokenProvider {

    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_TEAM_IDS = "teamIds";
    public static final String CLAIM_CUSTOMER_ID = "customerId";
    public static final String CLAIM_TOKEN_TYPE = "tokenType";

    public static final String TYPE_ACCESS = "ACCESS";
    public static final String TYPE_REFRESH = "REFRESH";

    private final JwtProperties properties;
    private final SecretKey signingKey;
    private final Clock clock;

    public JwtTokenProvider(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(HelpdeskUserPrincipal principal) {
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plusSeconds(properties.accessTokenExpirationSeconds());

        var builder = Jwts.builder()
                .subject(String.valueOf(principal.userId()))
                .claim(CLAIM_EMAIL, principal.email())
                .claim(CLAIM_ROLES, principal.roles())
                .claim(CLAIM_TEAM_IDS, principal.teamIds())
                .claim(CLAIM_TOKEN_TYPE, TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey);

        if (principal.customerId() != null) {
            builder.claim(CLAIM_CUSTOMER_ID, principal.customerId());
        }
        return builder.compact();
    }

    public String generateRefreshToken(Long userId, String email) {
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plusSeconds(properties.refreshTokenExpirationSeconds());

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_TOKEN_TYPE, TYPE_REFRESH)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    public HelpdeskUserPrincipal parseAccessToken(String token) {
        Claims claims = parseClaims(token);
        if (!TYPE_ACCESS.equals(claims.get(CLAIM_TOKEN_TYPE, String.class))) {
            throw new JwtException("Token is not an ACCESS token");
        }
        Long userId = Long.parseLong(claims.getSubject());
        String email = claims.get(CLAIM_EMAIL, String.class);
        Set<String> roles = extractStringSet(claims.get(CLAIM_ROLES));
        Set<Long> teamIds = extractLongSet(claims.get(CLAIM_TEAM_IDS));
        Long customerId = extractLong(claims.get(CLAIM_CUSTOMER_ID));
        return new HelpdeskUserPrincipal(userId, email, roles, teamIds, customerId);
    }

    public Long parseRefreshTokenUserId(String token) {
        Claims claims = parseClaims(token);
        if (!TYPE_REFRESH.equals(claims.get(CLAIM_TOKEN_TYPE, String.class))) {
            throw new JwtException("Token is not a REFRESH token");
        }
        return Long.parseLong(claims.getSubject());
    }

    public long getAccessTokenExpirationSeconds() {
        return properties.accessTokenExpirationSeconds();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .clock(() -> Date.from(Instant.now(clock)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Set<String> extractStringSet(Object raw) {
        if (raw instanceof Collection<?> col) {
            return col.stream().map(Object::toString).collect(Collectors.toUnmodifiableSet());
        }
        return Set.of();
    }

    private Set<Long> extractLongSet(Object raw) {
        if (raw instanceof Collection<?> col) {
            return col.stream()
                    .map(item -> Long.parseLong(item.toString()))
                    .collect(Collectors.toUnmodifiableSet());
        }
        return Set.of();
    }

    private Long extractLong(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number num) {
            return num.longValue();
        }
        return Long.parseLong(raw.toString());
    }
}
