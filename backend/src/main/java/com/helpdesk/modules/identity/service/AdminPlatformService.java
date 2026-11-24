package com.helpdesk.modules.identity.service;

import com.helpdesk.common.exception.BusinessException;
import com.helpdesk.common.exception.ErrorCode;
import com.helpdesk.common.security.HelpdeskUserPrincipal;
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
import com.helpdesk.modules.identity.entity.AppUser;
import com.helpdesk.modules.identity.entity.Category;
import com.helpdesk.modules.identity.entity.Channel;
import com.helpdesk.modules.identity.entity.ChannelAccount;
import com.helpdesk.modules.identity.entity.Role;
import com.helpdesk.modules.identity.entity.RoleCode;
import com.helpdesk.modules.identity.entity.SupportTeam;
import com.helpdesk.modules.identity.repository.AppUserRepository;
import com.helpdesk.modules.identity.repository.CategoryRepository;
import com.helpdesk.modules.identity.repository.ChannelAccountRepository;
import com.helpdesk.modules.identity.repository.ChannelRepository;
import com.helpdesk.modules.identity.repository.RoleRepository;
import com.helpdesk.modules.identity.repository.SupportTeamRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminPlatformService {

    private final AppUserRepository appUserRepository;
    private final RoleRepository roleRepository;
    private final SupportTeamRepository supportTeamRepository;
    private final CategoryRepository categoryRepository;
    private final ChannelRepository channelRepository;
    private final ChannelAccountRepository channelAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectProvider<CustomerAccountPort> customerAccountPortProvider;

    public AdminPlatformService(
            AppUserRepository appUserRepository,
            RoleRepository roleRepository,
            SupportTeamRepository supportTeamRepository,
            CategoryRepository categoryRepository,
            ChannelRepository channelRepository,
            ChannelAccountRepository channelAccountRepository,
            PasswordEncoder passwordEncoder,
            ObjectProvider<CustomerAccountPort> customerAccountPortProvider
    ) {
        this.appUserRepository = appUserRepository;
        this.roleRepository = roleRepository;
        this.supportTeamRepository = supportTeamRepository;
        this.categoryRepository = categoryRepository;
        this.channelRepository = channelRepository;
        this.channelAccountRepository = channelAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.customerAccountPortProvider = customerAccountPortProvider;
    }

    // ==================== USERS ====================

    @Transactional(readOnly = true)
    public List<UserProfileDto> listUsers(
            HelpdeskUserPrincipal principal,
            RoleCode roleFilter,
            Long teamIdFilter,
            Boolean isActive,
            Boolean isAvailable
    ) {
        List<Long> allowedTeamIds = null;
        if (!principal.hasRole("ADMIN")) {
            allowedTeamIds = supportTeamRepository.findSupervisedTeamIds(principal.userId());
            if (teamIdFilter != null && !allowedTeamIds.contains(teamIdFilter)) {
                throw BusinessException.forbidden(ErrorCode.TEAM_SCOPE_DENIED, "Supervisor không quản lý nhóm teamId=" + teamIdFilter);
            }
        }
        final List<Long> scopeTeams = allowedTeamIds;

        return appUserRepository.findAllByOrderByIdAsc().stream()
                .filter(u -> roleFilter == null || u.getRoles().stream().anyMatch(r -> r.getCode() == roleFilter))
                .filter(u -> isActive == null || u.isActive() == isActive)
                .filter(u -> isAvailable == null || u.isAvailable() == isAvailable)
                .map(this::toUserProfileDto)
                .filter(dto -> {
                    if (teamIdFilter != null) {
                        return dto.teamIds().contains(teamIdFilter);
                    }
                    if (scopeTeams != null) {
                        return dto.teamIds().stream().anyMatch(scopeTeams::contains);
                    }
                    return true;
                })
                .toList();
    }

    @Transactional
    public UserProfileDto createUser(AdminUserCreateRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        if (appUserRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw BusinessException.conflict(ErrorCode.DUPLICATE_RESOURCE, "Email đã tồn tại: " + normalizedEmail);
        }
        AppUser user = new AppUser(
                request.fullName().trim(),
                normalizedEmail,
                passwordEncoder.encode(request.password()),
                request.phone() != null ? request.phone().trim() : null
        );
        user.setAvailable(Boolean.TRUE.equals(request.available()));
        for (RoleCode rc : request.roles()) {
            Role role = roleRepository.findByCode(rc)
                    .orElseThrow(() -> BusinessException.notFound("Role", rc));
            user.addRole(role);
        }
        AppUser saved = appUserRepository.save(user);

        if (request.roles().contains(RoleCode.CUSTOMER)) {
            CustomerAccountPort port = customerAccountPortProvider.getIfAvailable();
            if (port != null) {
                port.createVerifiedCustomerForUser(saved.getId(), saved.getFullName(), saved.getEmail(), saved.getPhone());
            }
        }
        return toUserProfileDto(saved);
    }

    @Transactional
    public UserProfileDto updateUser(Long userId, AdminUserUpdateRequest request) {
        AppUser user = appUserRepository.findWithRolesById(userId)
                .orElseThrow(() -> BusinessException.notFound("AppUser", userId));

        if (request.fullName() != null && !request.fullName().isBlank()) {
            user.setFullName(request.fullName().trim());
        }
        if (request.phone() != null) {
            user.setPhone(request.phone().trim());
        }
        if (request.active() != null) {
            user.setActive(request.active());
        }
        if (request.available() != null) {
            user.setAvailable(request.available());
        }
        if (request.roles() != null && !request.roles().isEmpty()) {
            user.getRoles().clear();
            for (RoleCode rc : request.roles()) {
                Role role = roleRepository.findByCode(rc)
                        .orElseThrow(() -> BusinessException.notFound("Role", rc));
                user.addRole(role);
            }
        }
        return toUserProfileDto(appUserRepository.save(user));
    }

    // ==================== TEAMS & MEMBERS ====================

    @Transactional
    public TeamDto createTeam(AdminTeamCreateRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (supportTeamRepository.findByCode(code).isPresent()) {
            throw BusinessException.conflict(ErrorCode.DUPLICATE_RESOURCE, "Mã nhóm hỗ trợ đã tồn tại: " + code);
        }
        AppUser supervisor = appUserRepository.findWithRolesById(request.supervisorId())
                .orElseThrow(() -> BusinessException.notFound("Supervisor", request.supervisorId()));

        SupportTeam team = new SupportTeam(code, request.name().trim(), supervisor);
        team.addMember(supervisor);
        return toTeamDto(supportTeamRepository.save(team));
    }

    @Transactional
    public TeamDto updateTeam(Long teamId, AdminTeamUpdateRequest request) {
        SupportTeam team = supportTeamRepository.findById(teamId)
                .orElseThrow(() -> BusinessException.notFound("SupportTeam", teamId));
        if (request.name() != null && !request.name().isBlank()) {
            team.setName(request.name().trim());
        }
        if (request.supervisorId() != null) {
            AppUser sup = appUserRepository.findWithRolesById(request.supervisorId())
                    .orElseThrow(() -> BusinessException.notFound("Supervisor", request.supervisorId()));
            team.setSupervisor(sup);
        }
        if (request.active() != null) {
            team.setActive(request.active());
        }
        return toTeamDto(supportTeamRepository.save(team));
    }

    @Transactional
    public TeamDto addTeamMember(HelpdeskUserPrincipal principal, Long teamId, Long userId) {
        SupportTeam team = requireManageableTeam(principal, teamId);
        AppUser member = appUserRepository.findWithRolesById(userId)
                .orElseThrow(() -> BusinessException.notFound("AppUser", userId));
        team.addMember(member);
        return toTeamDto(supportTeamRepository.save(team));
    }

    @Transactional
    public TeamDto removeTeamMember(HelpdeskUserPrincipal principal, Long teamId, Long userId) {
        SupportTeam team = requireManageableTeam(principal, teamId);
        AppUser member = appUserRepository.findWithRolesById(userId)
                .orElseThrow(() -> BusinessException.notFound("AppUser", userId));
        team.removeMember(member);
        return toTeamDto(supportTeamRepository.save(team));
    }

    // ==================== CATEGORIES ====================

    @Transactional
    public CategoryDto createCategory(AdminCategoryCreateRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (categoryRepository.findByCode(code).isPresent()) {
            throw BusinessException.conflict(ErrorCode.DUPLICATE_RESOURCE, "Mã danh mục đã tồn tại: " + code);
        }
        Category saved = categoryRepository.save(new Category(code, request.name().trim()));
        return new CategoryDto(saved.getId(), saved.getCode(), saved.getName(), saved.isActive());
    }

    @Transactional
    public CategoryDto updateCategory(Long categoryId, AdminCategoryUpdateRequest request) {
        Category cat = categoryRepository.findById(categoryId)
                .orElseThrow(() -> BusinessException.notFound("Category", categoryId));
        if (request.name() != null && !request.name().isBlank()) {
            cat.setName(request.name().trim());
        }
        if (request.active() != null) {
            cat.setActive(request.active());
        }
        Category saved = categoryRepository.save(cat);
        return new CategoryDto(saved.getId(), saved.getCode(), saved.getName(), saved.isActive());
    }

    // ==================== CHANNELS & CHANNEL ACCOUNTS ====================

    @Transactional(readOnly = true)
    public List<ChannelWithAccountsDto> listChannelsWithAccounts() {
        List<Channel> channels = channelRepository.findAll();
        Map<Long, List<ChannelAccountDto>> accountsByChannel = channelAccountRepository.findAllByOrderByIdAsc()
                .stream()
                .collect(Collectors.groupingBy(
                        a -> a.getChannel().getId(),
                        Collectors.mapping(this::toChannelAccountDto, Collectors.toList())
                ));
        return channels.stream()
                .map(c -> new ChannelWithAccountsDto(
                        c.getId(),
                        c.getCode(),
                        c.getName(),
                        c.isActive(),
                        accountsByChannel.getOrDefault(c.getId(), List.of())
                ))
                .toList();
    }

    @Transactional
    public ChannelAccountDto createChannelAccount(ChannelAccountCreateRequest request) {
        String accCode = request.accountCode().trim().toUpperCase(Locale.ROOT);
        if (channelAccountRepository.existsByAccountCode(accCode)) {
            throw BusinessException.conflict(ErrorCode.DUPLICATE_RESOURCE, "Mã tài khoản kênh đã tồn tại: " + accCode);
        }
        Channel channel = channelRepository.findByCode(request.channelCode())
                .orElseThrow(() -> BusinessException.notFound("Channel", request.channelCode()));
        ChannelAccount saved = channelAccountRepository.save(
                new ChannelAccount(channel, accCode, request.displayName().trim(), request.configMetadata())
        );
        return toChannelAccountDto(saved);
    }

    @Transactional
    public ChannelAccountDto updateChannelAccount(Long id, ChannelAccountUpdateRequest request) {
        ChannelAccount account = channelAccountRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("ChannelAccount", id));
        if (request.displayName() != null && !request.displayName().isBlank()) {
            account.setDisplayName(request.displayName().trim());
        }
        if (request.configMetadata() != null) {
            account.setConfigMetadata(request.configMetadata());
        }
        if (request.active() != null) {
            account.setActive(request.active());
        }
        return toChannelAccountDto(channelAccountRepository.save(account));
    }

    // ==================== HELPERS ====================

    private SupportTeam requireManageableTeam(HelpdeskUserPrincipal principal, Long teamId) {
        SupportTeam team = supportTeamRepository.findById(teamId)
                .orElseThrow(() -> BusinessException.notFound("SupportTeam", teamId));
        if (!principal.hasRole("ADMIN") && !team.getSupervisor().getId().equals(principal.userId())) {
            throw BusinessException.forbidden(ErrorCode.TEAM_SCOPE_DENIED, "Chỉ Admin hoặc Supervisor phụ trách nhóm mới được quản lý thành viên.");
        }
        return team;
    }

    private UserProfileDto toUserProfileDto(AppUser user) {
        Set<String> roles = user.getRoles().stream().map(r -> r.getCode().name()).collect(Collectors.toSet());
        Set<Long> teamIds = supportTeamRepository.findActiveTeamIdsForUser(user.getId());
        CustomerAccountPort port = customerAccountPortProvider.getIfAvailable();
        Long customerId = (port != null) ? port.findCustomerIdByUserId(user.getId()).orElse(null) : null;
        return new UserProfileDto(
                user.getId(), user.getFullName(), user.getEmail(), user.getPhone(),
                roles, teamIds, customerId, user.isActive(), user.isAvailable()
        );
    }

    private TeamDto toTeamDto(SupportTeam team) {
        return new TeamDto(
                team.getId(),
                team.getCode(),
                team.getName(),
                team.getSupervisor().getId(),
                team.getSupervisor().getFullName(),
                team.getMembers().size(),
                team.isActive()
        );
    }

    private ChannelAccountDto toChannelAccountDto(ChannelAccount acc) {
        return new ChannelAccountDto(
                acc.getId(),
                acc.getChannel().getId(),
                acc.getChannel().getCode(),
                acc.getAccountCode(),
                acc.getDisplayName(),
                acc.getConfigMetadata(),
                acc.isActive(),
                acc.getCreatedAt()
        );
    }
}
