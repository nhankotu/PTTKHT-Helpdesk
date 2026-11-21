package com.helpdesk.modules.identity.controller;

import com.helpdesk.common.security.HelpdeskUserPrincipal;
import com.helpdesk.common.security.SecurityUtils;
import com.helpdesk.modules.identity.dto.AuthResponse;
import com.helpdesk.modules.identity.dto.AvailabilityRequest;
import com.helpdesk.modules.identity.dto.AvailabilityResponse;
import com.helpdesk.modules.identity.dto.LoginRequest;
import com.helpdesk.modules.identity.dto.RefreshTokenRequest;
import com.helpdesk.modules.identity.dto.RegisterRequest;
import com.helpdesk.modules.identity.dto.UserProfileDto;
import com.helpdesk.modules.identity.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "1. Identity & Auth (UC00)", description = "Đăng nhập JWT, đăng ký khách hàng, hồ sơ và trạng thái sẵn sàng")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Đăng nhập bằng email và mật khẩu")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/register")
    @Operation(summary = "Đăng ký tài khoản Khách hàng (CUSTOMER)")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registerCustomer(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Cấp lại Access Token từ Refresh Token")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    @Operation(summary = "Đăng xuất phiên hiện tại")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Lấy thông tin người dùng hiện tại")
    public ResponseEntity<UserProfileDto> me() {
        HelpdeskUserPrincipal principal = SecurityUtils.requireCurrentPrincipal();
        return ResponseEntity.ok(authService.getCurrentProfile(principal.userId()));
    }

    @PatchMapping("/me/availability")
    @PreAuthorize("hasAnyRole('AGENT', 'SUPERVISOR')")
    @Operation(summary = "Cập nhật trạng thái sẵn sàng nhận ticket tự động (UC04)")
    public ResponseEntity<AvailabilityResponse> updateAvailability(
            @Valid @RequestBody AvailabilityRequest request
    ) {
        HelpdeskUserPrincipal principal = SecurityUtils.requireCurrentPrincipal();
        return ResponseEntity.ok(authService.updateAvailability(principal.userId(), request.isAvailable()));
    }
}
