package com.helpdesk.modules.intake.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpdesk.common.exception.BusinessException;
import com.helpdesk.common.exception.ErrorCode;
import com.helpdesk.modules.identity.entity.ChannelCode;
import com.helpdesk.modules.identity.service.IdentityDirectoryService;
import com.helpdesk.modules.intake.adapter.EmailInboundAdapter;
import com.helpdesk.modules.intake.adapter.SocialChatInboundAdapter;
import com.helpdesk.modules.intake.dto.IntakeDtos.WebIntakeRequest;
import com.helpdesk.modules.intake.dto.IntakeDtos.WebIntakeResponse;
import com.helpdesk.modules.intake.dto.IntakeDtos.WebhookInboundRequest;
import com.helpdesk.modules.intake.dto.IntakeDtos.WebhookSender;
import com.helpdesk.modules.intake.entity.InboundEvent;
import com.helpdesk.modules.intake.repository.ConversationRepository;
import com.helpdesk.modules.intake.repository.CustomerIdentityRepository;
import com.helpdesk.modules.intake.repository.CustomerRepository;
import com.helpdesk.modules.intake.repository.InboundEventRepository;
import com.helpdesk.modules.intake.repository.OutboxEventRepository;
import com.helpdesk.modules.ticket.service.TicketIntakePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntakeServiceIdempotencyTest {

    @Mock private InboundEventRepository inboundEventRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private CustomerIdentityRepository customerIdentityRepository;
    @Mock private ConversationRepository conversationRepository;
    @Mock private IdentityDirectoryService identityDirectoryService;
    @Mock private TicketIntakePort ticketIntakePort;

    private IntakeService intakeService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Instant fixedNow = Instant.parse("2026-11-21T16:00:00Z");

    @BeforeEach
    void setUp() {
        intakeService = new IntakeService(
                inboundEventRepository,
                outboxEventRepository,
                customerRepository,
                customerIdentityRepository,
                conversationRepository,
                identityDirectoryService,
                ticketIntakePort,
                List.of(new EmailInboundAdapter(), new SocialChatInboundAdapter()),
                objectMapper,
                Clock.fixed(fixedNow, ZoneOffset.UTC),
                "test-webhook-secret"
        );
    }

    @Test
    @DisplayName("Idempotency: Gửi trùng Idempotency-Key cùng payload trả duplicate=true và không tạo ticket thứ hai")
    void duplicateIdempotencyKeySamePayloadReturnsPreviousResult() throws Exception {
        WebIntakeRequest req = new WebIntakeRequest("Lỗi thanh toán", "Chi tiết lỗi", 1L, null, List.of());
        String hash = sha256(objectMapper.writeValueAsString(req));

        InboundEvent existing = new InboundEvent(1L, "WEB:key-1", "key-1", hash, "{}", fixedNow);
        existing.markProcessed(1042L, 5001L, fixedNow);
        ReflectionTestUtils.setField(existing, "id", 9001L);

        when(inboundEventRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        WebIntakeResponse res = intakeService.submitWebRequest("key-1", req, null);

        assertThat(res.duplicate()).isTrue();
        assertThat(res.ticketId()).isEqualTo(1042L);
        verify(ticketIntakePort, never()).ingestInboundMessage(any());
    }

    @Test
    @DisplayName("Idempotency: Gửi trùng Idempotency-Key nhưng khác payload ném 409 IDEMPOTENCY_KEY_PAYLOAD_MISMATCH")
    void duplicateIdempotencyKeyDifferentPayloadThrows409() {
        WebIntakeRequest req = new WebIntakeRequest("Nội dung KHÁC", "Chi tiết khác", 1L, null, List.of());
        InboundEvent existing = new InboundEvent(1L, "WEB:key-1", "key-1", "different-hash", "{}", fixedNow);
        when(inboundEventRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> intakeService.submitWebRequest("key-1", req, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_PAYLOAD_MISMATCH);
    }

    @Test
    @DisplayName("Webhook: Từ chối 401 INVALID_WEBHOOK_SIGNATURE khi sai chữ ký")
    void invalidWebhookSignatureReturns401() {
        WebhookInboundRequest req = new WebhookInboundRequest(
                "FB_PAGE_MAIN", "evt-1", "thr-1", "msg-1",
                new WebhookSender("fb-1", "Hà", null, null),
                null, "Xin chào", fixedNow
        );

        assertThatThrownBy(() -> intakeService.processWebhook(ChannelCode.FACEBOOK, "wrong-secret", req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_WEBHOOK_SIGNATURE);
    }

    private String sha256(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
    }
}
