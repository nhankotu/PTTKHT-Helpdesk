package com.helpdesk.modules.ticket.controller;

import com.helpdesk.common.dto.PageResponse;
import com.helpdesk.common.security.SecurityUtils;
import com.helpdesk.modules.ticket.dto.StaffTicketDtos.MetadataUpdateRequest;
import com.helpdesk.modules.ticket.dto.StaffTicketDtos.StaffMessageCreateRequest;
import com.helpdesk.modules.ticket.dto.StaffTicketDtos.StaffMessageCreateResponse;
import com.helpdesk.modules.ticket.dto.StaffTicketDtos.StaffMessageDto;
import com.helpdesk.modules.ticket.dto.StaffTicketDtos.StaffTicketDetailDto;
import com.helpdesk.modules.ticket.dto.StaffTicketDtos.StatusUpdateRequest;
import com.helpdesk.modules.ticket.dto.StaffTicketDtos.TicketEventDto;
import com.helpdesk.modules.ticket.dto.StaffTicketDtos.TransferTicketRequest;
import com.helpdesk.modules.ticket.entity.MessageVisibility;
import com.helpdesk.modules.ticket.entity.TicketPriority;
import com.helpdesk.modules.ticket.entity.TicketStatus;
import com.helpdesk.modules.ticket.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tickets")
@PreAuthorize("hasAnyRole('AGENT', 'SUPERVISOR', 'ADMIN')")
@Tag(name = "3B. Staff Ticket Workspace (UC03)", description = "Không gian xử lý ticket cho Agent/Supervisor/Admin — Optimistic Locking & State Machine Guard")
public class StaffTicketController {

    private final TicketService ticketService;

    public StaffTicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping
    @Operation(summary = "Danh sách ticket theo phạm vi phân quyền")
    public ResponseEntity<PageResponse<StaffTicketDetailDto>> searchTickets(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) TicketPriority priority,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) Long assignedAgentId,
            @RequestParam(defaultValue = "false") boolean unassignedOnly,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(ticketService.searchStaffTickets(
                SecurityUtils.requireCurrentPrincipal(),
                status, priority, categoryId, teamId, assignedAgentId, unassignedOnly, q, pageable
        ));
    }

    @GetMapping("/{ticketId}")
    @Operation(summary = "Chi tiết ticket cho nhân viên (kèm version và allowedTransitions)")
    public ResponseEntity<StaffTicketDetailDto> getTicketDetail(@PathVariable Long ticketId) {
        return ResponseEntity.ok(ticketService.getStaffTicketDetail(SecurityUtils.requireCurrentPrincipal(), ticketId));
    }

    @GetMapping("/{ticketId}/messages")
    @Operation(summary = "Lịch sử hội thoại đầy đủ (PUBLIC & INTERNAL)")
    public ResponseEntity<List<StaffMessageDto>> listMessages(
            @PathVariable Long ticketId,
            @RequestParam(required = false) MessageVisibility visibility
    ) {
        return ResponseEntity.ok(ticketService.listStaffMessages(SecurityUtils.requireCurrentPrincipal(), ticketId, visibility));
    }

    @PostMapping("/{ticketId}/messages")
    @Operation(summary = "Gửi phản hồi PUBLIC hoặc thêm ghi chú nội bộ INTERNAL (hỗ trợ đổi trạng thái nguyên tử)")
    public ResponseEntity<StaffMessageCreateResponse> postMessage(
            @PathVariable Long ticketId,
            @Valid @RequestBody StaffMessageCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ticketService.postStaffMessage(SecurityUtils.requireCurrentPrincipal(), ticketId, request));
    }

    @PostMapping("/{ticketId}/messages/{messageId}/retry")
    @Operation(summary = "Thử gửi lại tin nhắn PUBLIC có trạng thái FAILED (UC03-E1)")
    public ResponseEntity<StaffMessageDto> retryFailedMessage(
            @PathVariable Long ticketId,
            @PathVariable Long messageId
    ) {
        return ResponseEntity.ok(ticketService.retryFailedMessage(SecurityUtils.requireCurrentPrincipal(), ticketId, messageId));
    }

    @PatchMapping("/{ticketId}/status")
    @Operation(summary = "Cập nhật trạng thái ticket qua State Machine Guard (bắt buộc version)")
    public ResponseEntity<StaffTicketDetailDto> updateStatus(
            @PathVariable Long ticketId,
            @Valid @RequestBody StatusUpdateRequest request
    ) {
        return ResponseEntity.ok(ticketService.updateStatus(SecurityUtils.requireCurrentPrincipal(), ticketId, request));
    }

    @PatchMapping("/{ticketId}/metadata")
    @Operation(summary = "Cập nhật priority / categoryId (bắt buộc version)")
    public ResponseEntity<StaffTicketDetailDto> updateMetadata(
            @PathVariable Long ticketId,
            @Valid @RequestBody MetadataUpdateRequest request
    ) {
        return ResponseEntity.ok(ticketService.updateMetadata(SecurityUtils.requireCurrentPrincipal(), ticketId, request));
    }

    @PostMapping("/{ticketId}/transfer")
    @Operation(summary = "Chuyển giao ticket sang nhóm/Agent khác (bắt buộc version & thuộc team_member)")
    public ResponseEntity<StaffTicketDetailDto> transfer(
            @PathVariable Long ticketId,
            @Valid @RequestBody TransferTicketRequest request
    ) {
        return ResponseEntity.ok(ticketService.transferTicket(SecurityUtils.requireCurrentPrincipal(), ticketId, request));
    }

    @GetMapping("/{ticketId}/events")
    @Operation(summary = "Lịch sử kiểm toán thay đổi của ticket")
    public ResponseEntity<List<TicketEventDto>> listEvents(@PathVariable Long ticketId) {
        return ResponseEntity.ok(ticketService.listTicketEvents(SecurityUtils.requireCurrentPrincipal(), ticketId));
    }
}
