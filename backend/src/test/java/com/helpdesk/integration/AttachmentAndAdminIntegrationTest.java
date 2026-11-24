package com.helpdesk.integration;

import com.helpdesk.modules.identity.dto.AdminDtos.AdminCategoryCreateRequest;
import com.helpdesk.modules.identity.dto.AdminDtos.ChannelWithAccountsDto;
import com.helpdesk.modules.identity.dto.AuthResponse;
import com.helpdesk.modules.identity.dto.CategoryDto;
import com.helpdesk.modules.identity.dto.LoginRequest;
import com.helpdesk.modules.intake.dto.IntakeDtos.ContactInfo;
import com.helpdesk.modules.intake.dto.IntakeDtos.WebIntakeRequest;
import com.helpdesk.modules.intake.dto.IntakeDtos.WebIntakeResponse;
import com.helpdesk.modules.ticket.dto.StaffTicketDtos.StaffMessageCreateRequest;
import com.helpdesk.modules.ticket.dto.StaffTicketDtos.StaffMessageCreateResponse;
import com.helpdesk.modules.ticket.entity.MessageVisibility;
import com.helpdesk.modules.ticket.service.AttachmentService.AttachmentDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AttachmentAndAdminIntegrationTest extends AbstractMySqlIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("Admin CRUD (Category, Channels) hoạt động và CUSTOMER bị chặn 403 khi tải file đính kèm thuộc tin INTERNAL")
    void adminCrudAndInternalAttachmentIsolation() {
        String adminToken = login("admin@helpdesk.local", "Password123!");
        String agentToken = login("agent.an@helpdesk.local", "Password123!");
        String customerToken = login("customer@helpdesk.local", "Password123!");

        // 1. Admin tạo Category mới & xem danh sách Channels
        HttpHeaders adminHeaders = bearer(adminToken);
        ResponseEntity<CategoryDto> createdCat = restTemplate.postForEntity(
                "/api/v1/admin/categories",
                new HttpEntity<>(new AdminCategoryCreateRequest("BILLING_VIP", "Thanh toán VIP"), adminHeaders),
                CategoryDto.class
        );
        assertThat(createdCat.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createdCat.getBody().code()).isEqualTo("BILLING_VIP");

        ResponseEntity<ChannelWithAccountsDto[]> channelsRes = restTemplate.exchange(
                "/api/v1/admin/channels", HttpMethod.GET, new HttpEntity<>(adminHeaders), ChannelWithAccountsDto[].class
        );
        assertThat(channelsRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(channelsRes.getBody()).isNotEmpty();

        // 2. Customer tạo ticket, Agent tạo 1 ghi chú INTERNAL và upload file đính kèm vào ghi chú đó
        HttpHeaders custHeaders = bearer(customerToken);
        custHeaders.set("Idempotency-Key", "idem-attach-e2e-001");
        ResponseEntity<WebIntakeResponse> intakeRes = restTemplate.postForEntity(
                "/api/v1/intake/web-requests",
                new HttpEntity<>(new WebIntakeRequest(
                        "Yêu cầu đính kèm tài liệu",
                        "Kiểm tra đính kèm tệp.",
                        null,
                        new ContactInfo("Trần Minh Quân", "customer@helpdesk.local", "0900000004"),
                        List.of()
                ), custHeaders),
                WebIntakeResponse.class
        );
        assertThat(intakeRes.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        Long ticketId = intakeRes.getBody().ticketId();

        ResponseEntity<StaffMessageCreateResponse> internalMsgRes = restTemplate.postForEntity(
                "/api/v1/tickets/" + ticketId + "/messages",
                new HttpEntity<>(new StaffMessageCreateRequest(
                        MessageVisibility.INTERNAL, "Đính kèm báo cáo nội bộ", null, null, null
                ), bearer(agentToken)),
                StaffMessageCreateResponse.class
        );
        assertThat(internalMsgRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long internalMessageId = internalMsgRes.getBody().message().id();

        // Upload file vào tin INTERNAL bằng quyền AGENT
        HttpHeaders multipartHeaders = bearer(agentToken);
        multipartHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("messageId", internalMessageId.toString());
        body.add("file", new ByteArrayResource("SECRET_INTERNAL_FILE_CONTENT".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "internal-audit.txt";
            }
        });

        ResponseEntity<AttachmentDto> uploaded = restTemplate.postForEntity(
                "/api/v1/attachments", new HttpEntity<>(body, multipartHeaders), AttachmentDto.class
        );
        assertThat(uploaded.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long attachmentId = uploaded.getBody().id();

        // Agent tải được file (200 OK)
        ResponseEntity<byte[]> agentDownload = restTemplate.exchange(
                "/api/v1/attachments/" + attachmentId + "/download",
                HttpMethod.GET,
                new HttpEntity<>(bearer(agentToken)),
                byte[].class
        );
        assertThat(agentDownload.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(agentDownload.getBody(), StandardCharsets.UTF_8)).isEqualTo("SECRET_INTERNAL_FILE_CONTENT");

        // HARD INVARIANT: Customer bị chặn 403 FORBIDDEN khi cố tải file thuộc tin INTERNAL
        ResponseEntity<String> custDownload = restTemplate.exchange(
                "/api/v1/attachments/" + attachmentId + "/download",
                HttpMethod.GET,
                new HttpEntity<>(bearer(customerToken)),
                String.class
        );
        assertThat(custDownload.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private String login(String email, String password) {
        ResponseEntity<AuthResponse> res = restTemplate.postForEntity(
                "/api/v1/auth/login", new LoginRequest(email, password), AuthResponse.class
        );
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return res.getBody().accessToken();
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
