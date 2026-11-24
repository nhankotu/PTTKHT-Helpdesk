package com.helpdesk.modules.identity.service;

import com.helpdesk.modules.identity.dto.CategoryDto;
import com.helpdesk.modules.identity.dto.TeamDto;
import com.helpdesk.modules.identity.entity.Category;
import com.helpdesk.modules.identity.entity.SupportTeam;
import com.helpdesk.modules.identity.repository.CategoryRepository;
import com.helpdesk.modules.identity.repository.SupportTeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReferenceDataService {

    private final CategoryRepository categoryRepository;
    private final SupportTeamRepository supportTeamRepository;

    public ReferenceDataService(
            CategoryRepository categoryRepository,
            SupportTeamRepository supportTeamRepository
    ) {
        this.categoryRepository = categoryRepository;
        this.supportTeamRepository = supportTeamRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> listCategories(boolean activeOnly) {
        List<Category> categories = activeOnly
                ? categoryRepository.findByActiveTrueOrderByNameAsc()
                : categoryRepository.findAllByOrderByNameAsc();
        return categories.stream()
                .map(c -> new CategoryDto(c.getId(), c.getCode(), c.getName(), c.isActive()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TeamDto> listTeams() {
        List<SupportTeam> teams = supportTeamRepository.findAllByOrderByNameAsc();
        return teams.stream()
                .map(t -> new TeamDto(
                        t.getId(),
                        t.getCode(),
                        t.getName(),
                        t.getSupervisor().getId(),
                        t.getSupervisor().getFullName(),
                        t.getMembers().size(),
                        t.isActive()
                ))
                .toList();
    }
}
