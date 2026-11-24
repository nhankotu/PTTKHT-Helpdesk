package com.helpdesk.integration;

import com.helpdesk.modules.identity.dto.AuthResponse;
import com.helpdesk.modules.identity.dto.LoginRequest;
import com.helpdesk.modules.identity.dto.RegisterRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaAndAuthIntegrationTest extends AbstractMySqlIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("Flyway V1+V2 chạy xanh trên MySQL 8.0 thật và luồng Register -> Login -> /me hoạt động đầy đủ")
    void flywaySchemaValidationAndAuthFlow() {
        RegisterRequest registerReq = new RegisterRequest(
                "Nguyễn Khách Hàng",
                "khachhang.e2e@example.com",
                "StrongPass123!",
                "0911223344"
        );

        ResponseEntity<AuthResponse> registerRes = restTemplate.postForEntity(
                "/api/v1/auth/register", registerReq, AuthResponse.class
        );
        assertThat(registerRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registerRes.getBody()).isNotNull();
        assertThat(registerRes.getBody().accessToken()).isNotBlank();
        assertThat(registerRes.getBody().user().roles()).containsExactly("CUSTOMER");

        ResponseEntity<AuthResponse> loginRes = restTemplate.postForEntity(
                "/api/v1/auth/login",
                new LoginRequest("khachhang.e2e@example.com", "StrongPass123!"),
                AuthResponse.class
        );
        assertThat(loginRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginRes.getBody()).isNotNull();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(loginRes.getBody().accessToken());
        ResponseEntity<String> meRes = restTemplate.exchange(
                "/api/v1/auth/me", HttpMethod.GET, new HttpEntity<>(headers), String.class
        );
        assertThat(meRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(meRes.getBody()).contains("khachhang.e2e@example.com");

        ResponseEntity<String> categoriesRes = restTemplate.getForEntity("/api/v1/categories", String.class);
        assertThat(categoriesRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(categoriesRes.getBody()).contains("PAYMENT");
    }
}
