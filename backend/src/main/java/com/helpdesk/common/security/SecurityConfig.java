package com.helpdesk.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpdesk.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final List<String> allowedOrigins;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:3000}") String allowedOriginsCsv
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.allowedOrigins = Arrays.stream(allowedOriginsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint((req, res, ex) ->
                                writeProblem(res, req, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED,
                                        "Phiên đăng nhập không hợp lệ hoặc đã hết hạn."))
                        .accessDeniedHandler((req, res, ex) ->
                                writeProblem(res, req, HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN,
                                        "Bạn không có quyền truy cập tài nguyên này.")))
                .authorizeHttpRequests(auth -> auth
                        // OpenAPI, Swagger UI & Actuator Health
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/actuator/health").permitAll()
                        // Public Auth endpoints
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/refresh").permitAll()
                        // Public Reference Data & Public KB & Web Intake / External Webhooks
                        .requestMatchers(HttpMethod.GET, "/api/v1/categories", "/api/v1/customer/kb/articles/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/intake/web-requests", "/api/v1/webhooks/**").permitAll()
                        // Customer Portal (strictly CUSTOMER)
                        .requestMatchers("/api/v1/customer/tickets/**").hasRole("CUSTOMER")
                        // Staff Workspace (never CUSTOMER)
                        .requestMatchers("/api/v1/tickets/**", "/api/v1/notifications/**", "/api/v1/ai/**").hasAnyRole("AGENT", "SUPERVISOR", "ADMIN")
                        .requestMatchers("/api/v1/kb/**").hasAnyRole("AGENT", "SUPERVISOR", "ADMIN")
                        // Supervisor & Admin scopes
                        .requestMatchers("/api/v1/reports/**", "/api/v1/routing/**", "/api/v1/sla/**").hasAnyRole("SUPERVISOR", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/users", "/api/v1/admin/channels", "/api/v1/admin/inbound-events").hasAnyRole("SUPERVISOR", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/admin/teams/*/members/*").hasAnyRole("SUPERVISOR", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/admin/teams/*/members/*").hasAnyRole("SUPERVISOR", "ADMIN")
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        // Attachments (upload/download checked in service layer)
                        .requestMatchers("/api/v1/attachments/**").authenticated()
                        // All other endpoints require authentication
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Request-Id"));
        config.setExposedHeaders(List.of("X-Request-Id"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private void writeProblem(
            HttpServletResponse response,
            HttpServletRequest request,
            HttpStatus status,
            ErrorCode errorCode,
            String detail
    ) throws IOException {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(status.getReasonPhrase());
        pd.setType(URI.create("https://helpdesk.local/errors/" + errorCode.name().toLowerCase().replace('_', '-')));
        pd.setInstance(URI.create(request.getRequestURI()));
        pd.setProperty("errorCode", errorCode.name());
        pd.setProperty("timestamp", Instant.now(clock).toString());
        pd.setProperty("traceId", "req-" + UUID.randomUUID().toString().substring(0, 8));

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), pd);
    }
}
