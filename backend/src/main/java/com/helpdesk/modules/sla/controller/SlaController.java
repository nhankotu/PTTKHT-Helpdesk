package com.helpdesk.modules.sla.controller;

import com.helpdesk.common.dto.PageResponse;
import com.helpdesk.common.security.SecurityUtils;
import com.helpdesk.modules.sla.dto.SlaDtos.EscalationActionCreateRequest;
import com.helpdesk.modules.sla.dto.SlaDtos.EscalationActionDto;
import com.helpdesk.modules.sla.dto.SlaDtos.SlaEventDto;
import com.helpdesk.modules.sla.dto.SlaDtos.SlaPolicyCreateRequest;
import com.helpdesk.modules.sla.dto.SlaDtos.SlaPolicyDto;
import com.helpdesk.modules.sla.dto.SlaDtos.TicketSlaDetailDto;
import com.helpdesk.modules.sla.entity.SlaEventType;
import com.helpdesk.modules.sla.service.SlaService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "5. SLA & Escalations (UC05)", description = "Chính sách SLA, tiến độ 2 mốc theo ticket, cảnh báo và xử lý chuyển cấp")
public class SlaController {

    private final SlaService slaService;

    public SlaController(SlaService slaService) {
        this.slaService = slaService;
    }

    @GetMapping("/sla/policies")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    @Operation(summary = "Danh sách chính sách SLA (kèm 2 mốc FIRST_RESPONSE & RESOLUTION)")
    public ResponseEntity<List<SlaPolicyDto>> listPolicies() {
        return ResponseEntity.ok(slaService.listPolicies());
    }

    @PostMapping("/admin/sla/policies")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tạo phiên bản mới của chính sách SLA theo priority (không ảnh hưởng ticket cũ)")
    public ResponseEntity<SlaPolicyDto> createPolicy(@Valid @RequestBody SlaPolicyCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(slaService.createNewPolicyVersion(request));
    }

    @GetMapping("/tickets/{ticketId}/sla")
    @PreAuthorize("hasAnyRole('AGENT', 'SUPERVISOR', 'ADMIN')")
    @Operation(summary = "Xem chi tiết 2 mốc SLA, lịch sử pause, cảnh báo và chuyển cấp của ticket")
    public ResponseEntity<List<TicketSlaDetailDto>> getTicketSla(@PathVariable Long ticketId) {
        return ResponseEntity.ok(slaService.getTicketSlaDetails(ticketId));
    }

    @GetMapping("/sla/escalations")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    @Operation(summary = "Danh sách các sự kiện cảnh báo / vi phạm SLA")
    public ResponseEntity<PageResponse<SlaEventDto>> listEscalations(
            @RequestParam(required = false) SlaEventType eventType,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(slaService.listEscalationEvents(eventType, pageable));
    }

    @PostMapping("/sla/events/{slaEventId}/escalations")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    @Operation(summary = "Ghi nhận hành động xử lý chuyển cấp cho một sự kiện SLA")
    public ResponseEntity<EscalationActionDto> recordEscalation(
            @PathVariable Long slaEventId,
            @Valid @RequestBody EscalationActionCreateRequest request
    ) {
        Long userId = SecurityUtils.requireCurrentPrincipal().userId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(slaService.recordEscalationAction(slaEventId, userId, request));
    }
}
