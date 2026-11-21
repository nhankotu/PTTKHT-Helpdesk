package com.helpdesk.modules.intake.controller;

import com.helpdesk.common.dto.PageResponse;
import com.helpdesk.common.security.SecurityUtils;
import com.helpdesk.modules.identity.entity.ChannelCode;
import com.helpdesk.modules.intake.dto.IntakeDtos.InboundEventSummaryDto;
import com.helpdesk.modules.intake.dto.IntakeDtos.WebIntakeRequest;
import com.helpdesk.modules.intake.dto.IntakeDtos.WebIntakeResponse;
import com.helpdesk.modules.intake.dto.IntakeDtos.WebhookInboundRequest;
import com.helpdesk.modules.intake.dto.IntakeDtos.WebhookInboundResponse;
import com.helpdesk.modules.intake.entity.InboundProcessStatus;
import com.helpdesk.modules.intake.service.IntakeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "2. Omnichannel Intake & Webhooks (UC01-UC02)", description = "Tiếp nhận Web Form (Idempotency-Key) và Webhook đa kênh")
public class IntakeController {

    private final IntakeService intakeService;

    public IntakeController(IntakeService intakeService) {
        this.intakeService = intakeService;
    }

    @PostMapping("/intake/web-requests")
    @Operation(summary = "UC01: Gửi yêu cầu hỗ trợ qua Web Form (bắt buộc Header Idempotency-Key)")
    public ResponseEntity<WebIntakeResponse> submitWebRequest(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody WebIntakeRequest request
    ) {
        WebIntakeResponse response = intakeService.submitWebRequest(
                idempotencyKey,
                request,
                SecurityUtils.getCurrentPrincipalOptional().orElse(null)
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/intake/web-requests/{requestId}")
    @Operation(summary = "Tra cứu trạng thái yêu cầu tiếp nhận (inbound_event)")
    public ResponseEntity<WebIntakeResponse> getWebRequestStatus(@PathVariable Long requestId) {
        return ResponseEntity.ok(intakeService.getWebRequestStatus(requestId));
    }

    @PostMapping("/webhooks/{channelCode}")
    @Operation(summary = "UC02: Nhận sự kiện webhook từ kênh ngoài (EMAIL, LIVE_CHAT, FACEBOOK, ZALO)")
    public ResponseEntity<WebhookInboundResponse> receiveWebhook(
            @PathVariable ChannelCode channelCode,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
            @Valid @RequestBody WebhookInboundRequest request
    ) {
        WebhookInboundResponse response = intakeService.processWebhook(channelCode, signature, request);
        HttpStatus status = response.duplicate() ? HttpStatus.OK : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/admin/inbound-events")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    @Operation(summary = "Giám sát nhật ký inbound_event (Admin/Supervisor)")
    public ResponseEntity<PageResponse<InboundEventSummaryDto>> listInboundEvents(
            @RequestParam(required = false) InboundProcessStatus processStatus,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(intakeService.listInboundEvents(processStatus, pageable));
    }
}
