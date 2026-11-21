package com.helpdesk.modules.sla.controller;

import com.helpdesk.common.dto.PageResponse;
import com.helpdesk.common.security.SecurityUtils;
import com.helpdesk.modules.sla.dto.SlaDtos.NotificationDto;
import com.helpdesk.modules.sla.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@PreAuthorize("hasAnyRole('AGENT', 'SUPERVISOR', 'ADMIN')")
@Tag(name = "5. Notifications (UC04-UC05)", description = "Thông báo phân công và cảnh báo/vi phạm SLA cho nhân viên")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(summary = "Danh sách thông báo của người dùng hiện tại")
    public ResponseEntity<PageResponse<NotificationDto>> listMyNotifications(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Long userId = SecurityUtils.requireCurrentPrincipal().userId();
        return ResponseEntity.ok(notificationService.listForRecipient(userId, unreadOnly, pageable));
    }

    @PatchMapping("/{notificationId}/read")
    @Operation(summary = "Đánh dấu 1 thông báo đã đọc")
    public ResponseEntity<NotificationDto> markRead(@PathVariable Long notificationId) {
        Long userId = SecurityUtils.requireCurrentPrincipal().userId();
        return ResponseEntity.ok(notificationService.markRead(userId, notificationId));
    }

    @PostMapping("/read-all")
    @Operation(summary = "Đánh dấu tất cả thông báo đã đọc")
    public ResponseEntity<Map<String, Integer>> markAllRead() {
        Long userId = SecurityUtils.requireCurrentPrincipal().userId();
        return ResponseEntity.ok(Map.of("updatedCount", notificationService.markAllRead(userId)));
    }
}
