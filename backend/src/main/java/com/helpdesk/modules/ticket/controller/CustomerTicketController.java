package com.helpdesk.modules.ticket.controller;

import com.helpdesk.common.dto.PageResponse;
import com.helpdesk.common.security.SecurityUtils;
import com.helpdesk.modules.ticket.dto.CustomerTicketDtos.CsatResponse;
import com.helpdesk.modules.ticket.dto.CustomerTicketDtos.CsatSubmitRequest;
import com.helpdesk.modules.ticket.dto.CustomerTicketDtos.CustomerCloseRequest;
import com.helpdesk.modules.ticket.dto.CustomerTicketDtos.CustomerMessageDto;
import com.helpdesk.modules.ticket.dto.CustomerTicketDtos.CustomerReopenRequest;
import com.helpdesk.modules.ticket.dto.CustomerTicketDtos.CustomerReplyRequest;
import com.helpdesk.modules.ticket.dto.CustomerTicketDtos.CustomerTicketDetailDto;
import com.helpdesk.modules.ticket.entity.TicketStatus;
import com.helpdesk.modules.ticket.service.TicketService;
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
@RequestMapping("/api/v1/customer/tickets")
@PreAuthorize("hasRole('CUSTOMER')")
@Tag(name = "3A. Customer Ticket Portal (UC03)", description = "Cổng khách hàng — chỉ trả tin nhắn PUBLIC, kiểm tra quyền sở hữu nghiêm ngặt")
public class CustomerTicketController {

    private final TicketService ticketService;

    public CustomerTicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping
    @Operation(summary = "Danh sách ticket của khách hàng hiện tại")
    public ResponseEntity<PageResponse<CustomerTicketDetailDto>> listMyTickets(
            @RequestParam(required = false) TicketStatus status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(ticketService.listCustomerTickets(SecurityUtils.requireCurrentPrincipal(), status, pageable));
    }

    @GetMapping("/{ticketId}")
    @Operation(summary = "Chi tiết ticket của khách hàng (không chứa dữ liệu nội bộ)")
    public ResponseEntity<CustomerTicketDetailDto> getMyTicket(@PathVariable Long ticketId) {
        return ResponseEntity.ok(ticketService.getCustomerTicketDetail(SecurityUtils.requireCurrentPrincipal(), ticketId));
    }

    @GetMapping("/{ticketId}/messages")
    @Operation(summary = "Lịch sử hội thoại PUBLIC của ticket (tuyệt đối không có ghi chú INTERNAL)")
    public ResponseEntity<List<CustomerMessageDto>> getMyTicketMessages(@PathVariable Long ticketId) {
        return ResponseEntity.ok(ticketService.listCustomerPublicMessages(SecurityUtils.requireCurrentPrincipal(), ticketId));
    }

    @PostMapping("/{ticketId}/messages")
    @Operation(summary = "Khách hàng gửi tin nhắn bổ sung thông tin")
    public ResponseEntity<CustomerMessageDto> reply(
            @PathVariable Long ticketId,
            @Valid @RequestBody CustomerReplyRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ticketService.addCustomerReply(SecurityUtils.requireCurrentPrincipal(), ticketId, request));
    }

    @PostMapping("/{ticketId}/confirm-close")
    @Operation(summary = "Khách hàng xác nhận đã giải quyết (RESOLVED -> CLOSED)")
    public ResponseEntity<CustomerTicketDetailDto> confirmClose(
            @PathVariable Long ticketId,
            @Valid @RequestBody CustomerCloseRequest request
    ) {
        return ResponseEntity.ok(ticketService.confirmCloseByCustomer(SecurityUtils.requireCurrentPrincipal(), ticketId, request));
    }

    @PostMapping("/{ticketId}/reopen")
    @Operation(summary = "Khách hàng mở lại ticket (RESOLVED -> IN_PROGRESS)")
    public ResponseEntity<CustomerTicketDetailDto> reopen(
            @PathVariable Long ticketId,
            @Valid @RequestBody CustomerReopenRequest request
    ) {
        return ResponseEntity.ok(ticketService.reopenByCustomer(SecurityUtils.requireCurrentPrincipal(), ticketId, request));
    }

    @PostMapping("/{ticketId}/csat")
    @Operation(summary = "Khách hàng gửi khảo sát hài lòng CSAT (1-5 sao)")
    public ResponseEntity<CsatResponse> submitCsat(
            @PathVariable Long ticketId,
            @Valid @RequestBody CsatSubmitRequest request
    ) {
        return ResponseEntity.ok(ticketService.submitCsat(SecurityUtils.requireCurrentPrincipal(), ticketId, request));
    }
}
