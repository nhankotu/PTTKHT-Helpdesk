package com.helpdesk.modules.identity.service;

import com.helpdesk.common.exception.BusinessException;
import com.helpdesk.modules.identity.entity.AppUser;
import com.helpdesk.modules.identity.entity.Category;
import com.helpdesk.modules.identity.entity.ChannelAccount;
import com.helpdesk.modules.identity.entity.SupportTeam;
import com.helpdesk.modules.identity.repository.AppUserRepository;
import com.helpdesk.modules.identity.repository.CategoryRepository;
import com.helpdesk.modules.identity.repository.ChannelAccountRepository;
import com.helpdesk.modules.identity.repository.SupportTeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Facade đọc dữ liệu danh mục & phân quyền nội bộ của module identity cho các module khác.
 * Giúp giữ chặt ranh giới Modular Monolith (không truy cập trực tiếp Repository của identity).
 */
@Service
@Transactional(readOnly = true)
public class IdentityDirectoryService {

    private final ChannelAccountRepository channelAccountRepository;
    private final CategoryRepository categoryRepository;
    private final SupportTeamRepository supportTeamRepository;
    private final AppUserRepository appUserRepository;

    public IdentityDirectoryService(
            ChannelAccountRepository channelAccountRepository,
            CategoryRepository categoryRepository,
            SupportTeamRepository supportTeamRepository,
            AppUserRepository appUserRepository
    ) {
        this.channelAccountRepository = channelAccountRepository;
        this.categoryRepository = categoryRepository;
        this.supportTeamRepository = supportTeamRepository;
        this.appUserRepository = appUserRepository;
    }

    public ChannelAccount requireActiveChannelAccount(String accountCode) {
        ChannelAccount account = channelAccountRepository.findByAccountCode(accountCode)
                .orElseThrow(() -> BusinessException.notFound("ChannelAccount", accountCode));
        if (!account.isActive() || !account.getChannel().isActive()) {
            throw BusinessException.unprocessable(
                    com.helpdesk.common.exception.ErrorCode.VALIDATION_ERROR,
                    "Kênh tiếp nhận đang tạm ngưng hoạt động: " + accountCode
            );
        }
        return account;
    }

    public Optional<Category> findCategoryById(Long categoryId) {
        if (categoryId == null) {
            return Optional.empty();
        }
        return categoryRepository.findById(categoryId);
    }

    public Category requireCategoryById(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> BusinessException.notFound("Category", categoryId));
    }

    public Optional<AppUser> findUserById(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return appUserRepository.findById(userId);
    }

    public Optional<SupportTeam> findTeamById(Long teamId) {
        if (teamId == null) {
            return Optional.empty();
        }
        return supportTeamRepository.findById(teamId);
    }

    public SupportTeam requireTeamById(Long teamId) {
        return supportTeamRepository.findById(teamId)
                .orElseThrow(() -> BusinessException.notFound("SupportTeam", teamId));
    }

    public boolean isUserMemberOfTeam(Long teamId, Long userId) {
        if (teamId == null || userId == null) {
            return false;
        }
        return supportTeamRepository.findActiveTeamIdsForUser(userId).contains(teamId);
    }

    public java.util.List<Category> findAllActiveCategories() {
        return categoryRepository.findByActiveTrueOrderByNameAsc();
    }

    public Optional<Category> findCategoryByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return categoryRepository.findByCode(code.trim().toUpperCase());
    }

    public java.util.List<Long> findAvailableActiveAgentIdsInTeam(Long teamId) {
        if (teamId == null) {
            return java.util.List.of();
        }
        return supportTeamRepository.findAvailableActiveMemberIds(teamId);
    }

    public Optional<Long> findSupervisorIdOfTeam(Long teamId) {
        if (teamId == null) {
            return Optional.empty();
        }
        return supportTeamRepository.findById(teamId).map(t -> t.getSupervisor().getId());
    }

    public java.util.List<Long> findSupervisedTeamIds(Long supervisorId) {
        if (supervisorId == null) {
            return java.util.List.of();
        }
        return supportTeamRepository.findSupervisedTeamIds(supervisorId);
    }
}
