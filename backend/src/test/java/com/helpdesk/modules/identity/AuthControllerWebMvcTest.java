package com.helpdesk.modules.identity;

import com.helpdesk.common.config.ClockConfig;
import com.helpdesk.common.exception.GlobalExceptionHandler;
import com.helpdesk.common.security.HelpdeskUserPrincipal;
import com.helpdesk.common.security.JwtAuthenticationFilter;
import com.helpdesk.common.security.JwtProperties;
import com.helpdesk.common.security.JwtTokenProvider;
import com.helpdesk.common.security.SecurityConfig;
import com.helpdesk.modules.identity.controller.AuthController;
import com.helpdesk.modules.identity.dto.AvailabilityResponse;
import com.helpdesk.modules.identity.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Set;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@Import({
        ClockConfig.class,
        GlobalExceptionHandler.class,
        SecurityConfig.class,
        JwtTokenProvider.class,
        JwtAuthenticationFilter.class,
        AuthControllerWebMvcTest.JwtPropsConfig.class
})
@TestPropertySource(properties = {
        "app.jwt.secret=TestSecretKeyMustBeAtLeast32BytesLongForHS256Algorithm!",
        "app.jwt.access-token-expiration-seconds=3600",
        "app.jwt.refresh-token-expiration-seconds=86400"
})
class AuthControllerWebMvcTest {

    @EnableConfigurationProperties(JwtProperties.class)
    static class JwtPropsConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private AuthService authService;

    @Test
    @DisplayName("POST /api/v1/auth/login sai định dạng email trả 400 RFC 7807 ProblemDetail")
    void loginValidationReturnsRfc7807() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "not-an-email", "password": "" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/auth/me không có JWT trả 401 RFC 7807")
    void meWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("PATCH /api/v1/auth/me/availability chặn CUSTOMER với 403 Forbidden")
    void availabilityForbiddenForCustomer() throws Exception {
        String customerToken = jwtTokenProvider.generateAccessToken(
                new HelpdeskUserPrincipal(99L, "cus@test.local", Set.of("CUSTOMER"), Set.of(), 50L)
        );

        mockMvc.perform(patch("/api/v1/auth/me/availability")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "isAvailable": true }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("PATCH /api/v1/auth/me/availability cho phép AGENT cập nhật trạng thái sẵn sàng")
    void availabilityAllowedForAgent() throws Exception {
        String agentToken = jwtTokenProvider.generateAccessToken(
                new HelpdeskUserPrincipal(12L, "agent@test.local", Set.of("AGENT"), Set.of(2L), null)
        );
        when(authService.updateAvailability(12L, true))
                .thenReturn(new AvailabilityResponse(12L, true, Instant.parse("2026-11-21T16:00:00Z")));

        mockMvc.perform(patch("/api/v1/auth/me/availability")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "isAvailable": true }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(12))
                .andExpect(jsonPath("$.isAvailable").value(true));
    }
}
