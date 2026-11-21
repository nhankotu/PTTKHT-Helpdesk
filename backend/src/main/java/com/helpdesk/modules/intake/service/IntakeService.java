package com.helpdesk.modules.intake.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpdesk.common.dto.PageResponse;
import com.helpdesk.common.exception.BusinessException;
import com.helpdesk.common.exception.ErrorCode;
import com.helpdesk.common.security.HelpdeskUserPrincipal;
import com.helpdesk.modules.identity.entity.ChannelAccount;
import com.helpdesk.modules.identity.entity.ChannelCode;
import com.helpdesk.modules.identity.service.IdentityDirectoryService;
import com.helpdesk.modules.intake.adapter.InboundChannelAdapter;
import com.helpdesk.modules.intake.adapter.NormalizedInboundMessage;
import com.helpdesk.modules.intake.dto.IntakeDtos.InboundEventSummaryDto;
import com.helpdesk.modules.intake.dto.IntakeDtos.WebIntakeRequest;
import com.helpdesk.modules.intake.dto.IntakeDtos.WebIntakeResponse;
import com.helpdesk.modules.intake.dto.IntakeDtos.WebhookInboundRequest;
import com.helpdesk.modules.intake.dto.IntakeDtos.WebhookInboundResponse;
import com.helpdesk.modules.intake.entity.Conversation;
import com.helpdesk.modules.intake.entity.Customer;
import com.helpdesk.modules.intake.entity.CustomerIdentity;
import com.helpdesk.modules.intake.entity.InboundEvent;
import com.helpdesk.modules.intake.entity.InboundProcessStatus;
import com.helpdesk.modules.intake.entity.OutboxEvent;
import com.helpdesk.modules.intake.repository.ConversationRepository;
import com.helpdesk.modules.intake.repository.CustomerIdentityRepository;
import com.helpdesk.modules.intake.repository.CustomerRepository;
import com.helpdesk.modules.intake.repository.InboundEventRepository;
import com.helpdesk.modules.intake.repository.OutboxEventRepository;
import com.helpdesk.modules.ticket.service.TicketIntakePort;
import com.helpdesk.modules.ticket.service.TicketIntakePort.IntakeTicketCommand;
import com.helpdesk.modules.ticket.service.TicketIntakePort.IntakeTicketResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class IntakeService {

    private static final String DEFAULT_WEB_ACCOUNT_CODE = "WEB_PORTAL_DEFAULT";

    private final InboundEventRepository inboundEventRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final CustomerRepository customerRepository;
    private final CustomerIdentityRepository customerIdentityRepository;
    private final ConversationRepository conversationRepository;
    private final IdentityDirectoryService identityDirectoryService;
    private final TicketIntakePort ticketIntakePort;
    private final List<InboundChannelAdapter> inboundAdapters;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String webhookSecret;

    public IntakeService(
            InboundEventRepository inboundEventRepository,
            OutboxEventRepository outboxEventRepository,
            CustomerRepository customerRepository,
            CustomerIdentityRepository customerIdentityRepository,
            ConversationRepository conversationRepository,
            IdentityDirectoryService identityDirectoryService,
            TicketIntakePort ticketIntakePort,
            List<InboundChannelAdapter> inboundAdapters,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${app.webhook.secret:dev-webhook-secret}") String webhookSecret
    ) {
        this.inboundEventRepository = inboundEventRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.customerRepository = customerRepository;
        this.customerIdentityRepository = customerIdentityRepository;
        this.conversationRepository = conversationRepository;
        this.identityDirectoryService = identityDirectoryService;
        this.ticketIntakePort = ticketIntakePort;
        this.inboundAdapters = inboundAdapters;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.webhookSecret = webhookSecret;
    }

    @Transactional
    public WebIntakeResponse submitWebRequest(
            String idempotencyKey,
            WebIntakeRequest request,
            HelpdeskUserPrincipal principalOrNull
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, ErrorCode.MISSING_HEADER,
                    "Header Idempotency-Key bắt buộc và tối đa 128 ký tự.");
        }

        String trimmedKey = idempotencyKey.trim();
        String payloadHash = sha256(toJsonSafe(request));

        Optional<InboundEvent> existingOpt = inboundEventRepository.findByIdempotencyKey(trimmedKey);
        if (existingOpt.isPresent()) {
            InboundEvent existing = existingOpt.get();
            if (existing.getPayloadHash() != null && !existing.getPayloadHash().equals(payloadHash)) {
                throw BusinessException.conflict(
                        ErrorCode.IDEMPOTENCY_KEY_PAYLOAD_MISMATCH,
                        "Idempotency-Key đã được sử dụng cho một nội dung yêu cầu khác."
                );
            }
            return new WebIntakeResponse(
                    existing.getId(),
                    existing.getIdempotencyKey(),
                    existing.getProcessStatus(),
                    existing.getTicketId(),
                    null,
                    true,
                    existing.getReceivedAt()
            );
        }

        ChannelAccount account = identityDirectoryService.requireActiveChannelAccount(DEFAULT_WEB_ACCOUNT_CODE);
        if (request.categoryId() != null) {
            identityDirectoryService.requireCategoryById(request.categoryId());
        }

        Instant now = Instant.now(clock);
        InboundEvent event = inboundEventRepository.save(new InboundEvent(
                account.getId(),
                "WEB:" + trimmedKey,
                trimmedKey,
                payloadHash,
                toJsonSafe(Map.of("subject", request.subject(), "categoryId", String.valueOf(request.categoryId()))),
                now
        ));

        Customer customer = resolveWebCustomer(principalOrNull, request);
        String extUserId = customer.getUserId() != null ? "user:" + customer.getUserId() : "guest:" + customer.getId();
        CustomerIdentity identity = customerIdentityRepository
                .findByChannelAccountIdAndExternalUserId(account.getId(), extUserId)
                .orElseGet(() -> customerIdentityRepository.save(
                        new CustomerIdentity(customer.getId(), account.getId(), extUserId, customer.getFullName())
                ));

        Conversation conversation = conversationRepository.save(
                new Conversation(account.getId(), identity.getId(), "web-thr-" + trimmedKey, now)
        );

        IntakeTicketResult ticketResult = ticketIntakePort.ingestInboundMessage(new IntakeTicketCommand(
                conversation.getId(),
                customer.getId(),
                account.getChannel().getId(),
                request.categoryId(),
                request.subject().trim(),
                request.description().trim(),
                "web-msg-" + trimmedKey,
                now
        ));

        event.markProcessed(ticketResult.ticketId(), ticketResult.messageId(), now);
        inboundEventRepository.save(event);

        if (ticketResult.createdNewTicket()) {
            writeTicketCreatedOutbox(ticketResult, account.getChannel().getCode(), now);
        }

        return new WebIntakeResponse(
                event.getId(),
                event.getIdempotencyKey(),
                event.getProcessStatus(),
                ticketResult.ticketId(),
                ticketResult.ticketCode(),
                false,
                event.getReceivedAt()
        );
    }

    @Transactional(readOnly = true)
    public WebIntakeResponse getWebRequestStatus(Long requestId) {
        InboundEvent event = inboundEventRepository.findById(requestId)
                .orElseThrow(() -> BusinessException.notFound("InboundEvent", requestId));
        return new WebIntakeResponse(
                event.getId(),
                event.getIdempotencyKey(),
                event.getProcessStatus(),
                event.getTicketId(),
                null,
                false,
                event.getReceivedAt()
        );
    }

    @Transactional
    public WebhookInboundResponse processWebhook(
            ChannelCode channelCode,
            String signatureHeader,
            WebhookInboundRequest request
    ) {
        // Kiểm tra chữ ký Webhook (nếu cấu hình yêu cầu; hỗ trợ dev secret mặc định)
        if (signatureHeader == null || !signatureHeader.equals(webhookSecret)) {
            throw new BusinessException(
                    HttpStatus.UNAUTHORIZED,
                    ErrorCode.INVALID_WEBHOOK_SIGNATURE,
                    "Chữ ký webhook (X-Webhook-Signature) không hợp lệ."
            );
        }

        InboundChannelAdapter adapter = inboundAdapters.stream()
                .filter(a -> a.supports(channelCode))
                .findFirst()
                .orElseThrow(() -> BusinessException.unprocessable(
                        ErrorCode.VALIDATION_ERROR,
                        "Không hỗ trợ webhook cho kênh: " + channelCode
                ));

        NormalizedInboundMessage normalized = adapter.normalize(channelCode, request);
        ChannelAccount account = identityDirectoryService.requireActiveChannelAccount(normalized.channelAccountCode());

        // Chống xử lý trùng theo UNIQUE(channel_account_id, external_event_id)
        Optional<InboundEvent> duplicateOpt = inboundEventRepository
                .findByChannelAccountIdAndExternalEventId(account.getId(), normalized.externalEventId());
        if (duplicateOpt.isPresent()) {
            InboundEvent existing = duplicateOpt.get();
            return new WebhookInboundResponse(
                    existing.getId(),
                    existing.getExternalEventId(),
                    existing.getProcessStatus(),
                    true,
                    "DUPLICATE_IGNORED",
                    existing.getTicketId(),
                    null,
                    existing.getMessageId()
            );
        }

        Instant now = Instant.now(clock);
        InboundEvent event = inboundEventRepository.save(new InboundEvent(
                account.getId(),
                normalized.externalEventId(),
                null,
                sha256(toJsonSafe(request)),
                toJsonSafe(Map.of("channel", channelCode.name(), "threadId", normalized.externalThreadId())),
                now
        ));

        // Không tự động gộp khách chưa xác minh (UC02-E3)
        CustomerIdentity identity = customerIdentityRepository
                .findByChannelAccountIdAndExternalUserId(account.getId(), normalized.externalUserId())
                .orElseGet(() -> {
                    Customer newUnverifiedCustomer = customerRepository.save(new Customer(
                            null, normalized.senderName(), normalized.senderEmail(), normalized.senderPhone(), false
                    ));
                    return customerIdentityRepository.save(new CustomerIdentity(
                            newUnverifiedCustomer.getId(), account.getId(), normalized.externalUserId(), normalized.senderName()
                    ));
                });

        Conversation conversation = conversationRepository
                .findByChannelAccountIdAndExternalThreadId(account.getId(), normalized.externalThreadId())
                .orElseGet(() -> conversationRepository.save(
                        new Conversation(account.getId(), identity.getId(), normalized.externalThreadId(), now)
                ));

        IntakeTicketResult result = ticketIntakePort.ingestInboundMessage(new IntakeTicketCommand(
                conversation.getId(),
                identity.getCustomerId(),
                account.getChannel().getId(),
                normalized.categoryId(),
                normalized.subject(),
                normalized.body(),
                normalized.externalMessageId(),
                normalized.occurredAt() != null ? normalized.occurredAt() : now
        ));

        event.markProcessed(result.ticketId(), result.messageId(), now);
        inboundEventRepository.save(event);

        if (result.createdNewTicket()) {
            writeTicketCreatedOutbox(result, channelCode, now);
        }

        return new WebhookInboundResponse(
                event.getId(),
                event.getExternalEventId(),
                event.getProcessStatus(),
                false,
                result.createdNewTicket() ? "TICKET_CREATED" : "MESSAGE_APPENDED",
                result.ticketId(),
                result.ticketCode(),
                result.messageId()
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<InboundEventSummaryDto> listInboundEvents(InboundProcessStatus status, Pageable pageable) {
        Page<InboundEvent> page = (status != null)
                ? inboundEventRepository.findByProcessStatusOrderByReceivedAtDesc(status, pageable)
                : inboundEventRepository.findAllByOrderByReceivedAtDesc(pageable);
        return PageResponse.from(page.map(e -> new InboundEventSummaryDto(
                e.getId(), e.getChannelAccountId(), e.getExternalEventId(), e.getIdempotencyKey(),
                e.getProcessStatus(), e.getTicketId(), e.getMessageId(), e.getErrorCode(),
                e.getReceivedAt(), e.getProcessedAt()
        )));
    }

    private Customer resolveWebCustomer(HelpdeskUserPrincipal principalOrNull, WebIntakeRequest request) {
        if (principalOrNull != null && principalOrNull.customerId() != null) {
            return customerRepository.findById(principalOrNull.customerId())
                    .orElseThrow(() -> BusinessException.notFound("Customer", principalOrNull.customerId()));
        }
        String name = request.contact() != null ? request.contact().fullName() : null;
        String email = request.contact() != null ? request.contact().email() : null;
        String phone = request.contact() != null ? request.contact().phone() : null;
        return customerRepository.save(new Customer(null, name, email, phone, false));
    }

    private void writeTicketCreatedOutbox(IntakeTicketResult result, ChannelCode channelCode, Instant now) {
        String payload = toJsonSafe(Map.of(
                "ticketId", result.ticketId(),
                "ticketCode", result.ticketCode(),
                "messageId", result.messageId(),
                "channelCode", channelCode.name(),
                "createdAt", now.toString()
        ));
        outboxEventRepository.save(new OutboxEvent("TICKET", result.ticketId(), "TicketCreated", payload, now));
    }

    private String toJsonSafe(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không thể tuần tự hóa JSON", e);
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
