package com.helpdesk.modules.identity.service;

import com.helpdesk.common.exception.BusinessException;
import com.helpdesk.common.exception.ErrorCode;
import com.helpdesk.common.security.HelpdeskUserPrincipal;
import com.helpdesk.common.security.JwtTokenProvider;
import com.helpdesk.modules.identity.dto.AuthResponse;
import com.helpdesk.modules.identity.dto.AvailabilityResponse;
import com.helpdesk.modules.identity.dto.LoginRequest;
import com.helpdesk.modules.identity.dto.RefreshTokenRequest;
import com.helpdesk.modules.identity.dto.RegisterRequest;
import com.helpdesk.modules.identity.dto.UserProfileDto;
import com.helpdesk.modules.identity.entity.AppUser;
import com.helpdesk.modules.identity.entity.Role;
import com.helpdesk.modules.identity.entity.RoleCode;
import com.helpdesk.modules.identity.repository.AppUserRepository;
import com.helpdesk.modules.identity.repository.RoleRepository;
import com.helpdesk.modules.identity.repository.SupportTeamRepository;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final RoleRepository roleRepository;
    private final SupportTeamRepository supportTeamRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectProvider<CustomerAccountPort> customerAccountPortProvider;
    private final Clock clock;

    public AuthService(
            AppUserRepository appUserRepository,
            RoleRepository roleRepository,
            SupportTeamRepository supportTeamRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            ObjectProvider<CustomerAccountPort> customerAccountPortProvider,
            Clock clock
    ) {
        this.appUserRepository = appUserRepository;
        this.roleRepository = roleRepository;
        this.supportTeamRepository = supportTeamRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.customerAccountPortProvider = customerAccountPortProvider;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        AppUser user = appUserRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.UNAUTHORIZED,
                        ErrorCode.INVALID_CREDENTIALS,
                        "Email hoặc mật khẩu không đúng."
                ));

        if (!user.isActive()) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    ErrorCode.FORBIDDEN,
                    "Tài khoản đã bị vô hiệu hóa."
            );
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(
                    HttpStatus.UNAUTHORIZED,
                    ErrorCode.INVALID_CREDENTIALS,
                    "Email hoặc mật khẩu không đúng."
            );
        }

        return issueTokens(user);
    }

    @Transactional
    public AuthResponse registerCustomer(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        if (appUserRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw BusinessException.conflict(
                    ErrorCode.DUPLICATE_RESOURCE,
                    "Email đã được sử dụng trong hệ thống."
            );
        }

        Role customerRole = roleRepository.findByCode(RoleCode.CUSTOMER)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        ErrorCode.INTERNAL_SERVER_ERROR,
                        "Thiếu dữ liệu vai trò CUSTOMER trong cơ sở dữ liệu."
                ));

        AppUser user = new AppUser(
                request.fullName().trim(),
                normalizedEmail,
                passwordEncoder.encode(request.password()),
                request.phone() != null ? request.phone().trim() : null
        );
        user.addRole(customerRole);
        AppUser savedUser = appUserRepository.save(user);

        CustomerAccountPort customerPort = customerAccountPortProvider.getIfAvailable();
        if (customerPort != null) {
            customerPort.createVerifiedCustomerForUser(
                    savedUser.getId(),
                    savedUser.getFullName(),
                    savedUser.getEmail(),
                    savedUser.getPhone()
            );
        }

        return issueTokens(savedUser);
    }

    @Transactional(readOnly = true)
    public AuthResponse refresh(RefreshTokenRequest request) {
        Long userId;
        try {
            userId = jwtTokenProvider.parseRefreshTokenUserId(request.refreshToken());
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BusinessException(
                    HttpStatus.UNAUTHORIZED,
                    ErrorCode.TOKEN_INVALID,
                    "Refresh token không hợp lệ hoặc đã hết hạn."
            );
        }

        AppUser user = appUserRepository.findWithRolesById(userId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.UNAUTHORIZED,
                        ErrorCode.UNAUTHORIZED,
                        "Tài khoản không tồn tại."
                ));

        if (!user.isActive()) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    ErrorCode.FORBIDDEN,
                    "Tài khoản đã bị vô hiệu hóa."
            );
        }

        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public UserProfileDto getCurrentProfile(Long userId) {
        AppUser user = appUserRepository.findWithRolesById(userId)
                .orElseThrow(() -> BusinessException.notFound("User", userId));
        return toUserProfileDto(user);
    }

    @Transactional
    public AvailabilityResponse updateAvailability(Long userId, boolean available) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User", userId));
        user.setAvailable(available);
        appUserRepository.save(user);
        return new AvailabilityResponse(user.getId(), user.isAvailable(), Instant.now(clock));
    }

    private AuthResponse issueTokens(AppUser user) {
        UserProfileDto profile = toUserProfileDto(user);
        HelpdeskUserPrincipal principal = new HelpdeskUserPrincipal(
                profile.id(),
                profile.email(),
                profile.roles(),
                profile.teamIds(),
                profile.customerId()
        );
        String accessToken = jwtTokenProvider.generateAccessToken(principal);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), user.getEmail());

        return new AuthResponse(
                accessToken,
                refreshToken,
                "Bearer",
                jwtTokenProvider.getAccessTokenExpirationSeconds(),
                profile
        );
    }

    private UserProfileDto toUserProfileDto(AppUser user) {
        Set<String> roles = user.getRoles().stream()
                .map(r -> r.getCode().name())
                .collect(Collectors.toUnmodifiableSet());
        Set<Long> teamIds = supportTeamRepository.findActiveTeamIdsForUser(user.getId());
        CustomerAccountPort customerPort = customerAccountPortProvider.getIfAvailable();
        Long customerId = customerPort != null
                ? customerPort.findCustomerIdByUserId(user.getId()).orElse(null)
                : null;

        return new UserProfileDto(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                roles,
                teamIds,
                customerId,
                user.isActive(),
                user.isAvailable()
        );
    }
}
