package com.helpdesk.modules.identity.controller;

import com.helpdesk.common.security.SecurityUtils;
import com.helpdesk.modules.identity.dto.AdminDtos.AdminCategoryCreateRequest;
import com.helpdesk.modules.identity.dto.AdminDtos.AdminCategoryUpdateRequest;
import com.helpdesk.modules.identity.dto.AdminDtos.AdminTeamCreateRequest;
import com.helpdesk.modules.identity.dto.AdminDtos.AdminTeamUpdateRequest;
import com.helpdesk.modules.identity.dto.AdminDtos.AdminUserCreateRequest;
import com.helpdesk.modules.identity.dto.AdminDtos.AdminUserUpdateRequest;
import com.helpdesk.modules.identity.dto.AdminDtos.ChannelAccountCreateRequest;
import com.helpdesk.modules.identity.dto.AdminDtos.ChannelAccountDto;
import com.helpdesk.modules.identity.dto.AdminDtos.ChannelAccountUpdateRequest;
import com.helpdesk.modules.identity.dto.AdminDtos.ChannelWithAccountsDto;
import com.helpdesk.modules.identity.dto.CategoryDto;
import com.helpdesk.modules.identity.dto.TeamDto;
import com.helpdesk.modules.identity.dto.UserProfileDto;
import com.helpdesk.modules.identity.entity.RoleCode;
import com.helpdesk.modules.identity.service.AdminPlatformService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "1B. Platform Admin CRUD (UC00 / FR01)", description = "Quản trị tài khoản nhân viên, nhóm hỗ trợ, danh mục và kênh tiếp nhận")
public class AdminPlatformController {

    private final AdminPlatformService adminPlatformService;

    public AdminPlatformController(AdminPlatformService adminPlatformService) {
        this.adminPlatformService = adminPlatformService;
    }

    @GetMapping("/users")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    @Operation(summary = "Danh sách tài khoản (lọc theo role, teamId, isActive, isAvailable)")
    public ResponseEntity<List<UserProfileDto>> listUsers(
            @RequestParam(required = false) RoleCode role,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) Boolean isAvailable
    ) {
        return ResponseEntity.ok(adminPlatformService.listUsers(
                SecurityUtils.requireCurrentPrincipal(), role, teamId, isActive, isAvailable
        ));
    }

    @PostMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tạo tài khoản nhân viên hoặc người dùng mới")
    public ResponseEntity<UserProfileDto> createUser(@Valid @RequestBody AdminUserCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminPlatformService.createUser(request));
    }

    @PatchMapping("/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cập nhật thông tin, khóa/mở khóa hoặc đổi vai trò người dùng")
    public ResponseEntity<UserProfileDto> updateUser(
            @PathVariable Long userId,
            @Valid @RequestBody AdminUserUpdateRequest request
    ) {
        return ResponseEntity.ok(adminPlatformService.updateUser(userId, request));
    }

    @PostMapping("/teams")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tạo nhóm hỗ trợ mới")
    public ResponseEntity<TeamDto> createTeam(@Valid @RequestBody AdminTeamCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminPlatformService.createTeam(request));
    }

    @PatchMapping("/teams/{teamId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cập nhật tên, trưởng nhóm hoặc trạng thái nhóm hỗ trợ")
    public ResponseEntity<TeamDto> updateTeam(
            @PathVariable Long teamId,
            @Valid @RequestBody AdminTeamUpdateRequest request
    ) {
        return ResponseEntity.ok(adminPlatformService.updateTeam(teamId, request));
    }

    @PutMapping("/teams/{teamId}/members/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    @Operation(summary = "Thêm thành viên (Agent) vào nhóm hỗ trợ")
    public ResponseEntity<TeamDto> addTeamMember(
            @PathVariable Long teamId,
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(adminPlatformService.addTeamMember(
                SecurityUtils.requireCurrentPrincipal(), teamId, userId
        ));
    }

    @DeleteMapping("/teams/{teamId}/members/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    @Operation(summary = "Xóa thành viên khỏi nhóm hỗ trợ")
    public ResponseEntity<TeamDto> removeTeamMember(
            @PathVariable Long teamId,
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(adminPlatformService.removeTeamMember(
                SecurityUtils.requireCurrentPrincipal(), teamId, userId
        ));
    }

    @PostMapping("/categories")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tạo danh mục yêu cầu mới")
    public ResponseEntity<CategoryDto> createCategory(@Valid @RequestBody AdminCategoryCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminPlatformService.createCategory(request));
    }

    @PatchMapping("/categories/{categoryId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cập nhật tên hoặc trạng thái danh mục")
    public ResponseEntity<CategoryDto> updateCategory(
            @PathVariable Long categoryId,
            @Valid @RequestBody AdminCategoryUpdateRequest request
    ) {
        return ResponseEntity.ok(adminPlatformService.updateCategory(categoryId, request));
    }

    @GetMapping("/channels")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    @Operation(summary = "Danh sách kênh tiếp nhận và các tài khoản kênh")
    public ResponseEntity<List<ChannelWithAccountsDto>> listChannels() {
        return ResponseEntity.ok(adminPlatformService.listChannelsWithAccounts());
    }

    @PostMapping("/channel-accounts")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cấu hình tài khoản kênh tiếp nhận mới")
    public ResponseEntity<ChannelAccountDto> createChannelAccount(
            @Valid @RequestBody ChannelAccountCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminPlatformService.createChannelAccount(request));
    }

    @PatchMapping("/channel-accounts/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cập nhật hoặc bật/tắt tài khoản kênh tiếp nhận")
    public ResponseEntity<ChannelAccountDto> updateChannelAccount(
            @PathVariable Long id,
            @Valid @RequestBody ChannelAccountUpdateRequest request
    ) {
        return ResponseEntity.ok(adminPlatformService.updateChannelAccount(id, request));
    }
}
