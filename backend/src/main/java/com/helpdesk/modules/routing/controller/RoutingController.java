package com.helpdesk.modules.routing.controller;

import com.helpdesk.common.dto.PageResponse;
import com.helpdesk.common.security.SecurityUtils;
import com.helpdesk.modules.routing.dto.RoutingDtos.AssignmentResultDto;
import com.helpdesk.modules.routing.dto.RoutingDtos.ClassificationResultDto;
import com.helpdesk.modules.routing.dto.RoutingDtos.ManualAssignRequest;
import com.helpdesk.modules.routing.dto.RoutingDtos.ReviewQueueItemDto;
import com.helpdesk.modules.routing.dto.RoutingDtos.RoutingRuleCreateRequest;
import com.helpdesk.modules.routing.dto.RoutingDtos.RoutingRuleDto;
import com.helpdesk.modules.routing.dto.RoutingDtos.RoutingRuleUpdateRequest;
import com.helpdesk.modules.routing.service.RoutingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "4. AI Classification & Routing (UC04)", description = "Luật định tuyến, hàng đợi Fallback Supervisor, phân công thủ công & phân loại AI")
public class RoutingController {

    private final RoutingService routingService;

    public RoutingController(RoutingService routingService) {
        this.routingService = routingService;
    }

    @GetMapping("/routing/rules")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    @Operation(summary = "Danh sách luật định tuyến (routing_rule)")
    public ResponseEntity<List<RoutingRuleDto>> listRules() {
        return ResponseEntity.ok(routingService.listRules());
    }

    @PostMapping("/admin/routing/rules")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tạo luật định tuyến mới (mỗi danh mục tối đa 1 luật active)")
    public ResponseEntity<RoutingRuleDto> createRule(@Valid @RequestBody RoutingRuleCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(routingService.createRule(request));
    }

    @PatchMapping("/admin/routing/rules/{ruleId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cập nhật luật định tuyến")
    public ResponseEntity<RoutingRuleDto> updateRule(
            @PathVariable Long ruleId,
            @Valid @RequestBody RoutingRuleUpdateRequest request
    ) {
        return ResponseEntity.ok(routingService.updateRule(ruleId, request));
    }

    @GetMapping("/routing/review-queue")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    @Operation(summary = "Hàng đợi Fallback chờ Supervisor phân công thủ công")
    public ResponseEntity<PageResponse<ReviewQueueItemDto>> listReviewQueue(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(routingService.listReviewQueue(pageable));
    }

    @PostMapping("/routing/tickets/{ticketId}/assign")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    @Operation(summary = "Supervisor phân công thủ công (NEW -> ASSIGNED, bắt buộc version)")
    public ResponseEntity<AssignmentResultDto> assignManually(
            @PathVariable Long ticketId,
            @Valid @RequestBody ManualAssignRequest request
    ) {
        Long supervisorId = SecurityUtils.requireCurrentPrincipal().userId();
        return ResponseEntity.ok(routingService.assignManually(ticketId, supervisorId, request));
    }

    @PostMapping("/routing/tickets/{ticketId}/reclassify")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    @Operation(summary = "Chạy lại AI phân loại & định tuyến tự động cho ticket")
    public ResponseEntity<ClassificationResultDto> reclassify(@PathVariable Long ticketId) {
        return ResponseEntity.ok(routingService.classifyAndAutoRoute(ticketId));
    }

    @GetMapping("/routing/tickets/{ticketId}/classifications")
    @PreAuthorize("hasAnyRole('AGENT', 'SUPERVISOR', 'ADMIN')")
    @Operation(summary = "Xem lịch sử kết quả phân loại AI của ticket")
    public ResponseEntity<List<ClassificationResultDto>> listClassifications(@PathVariable Long ticketId) {
        return ResponseEntity.ok(routingService.listTicketClassifications(ticketId));
    }
}
