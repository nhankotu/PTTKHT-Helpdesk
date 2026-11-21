package com.helpdesk.modules.identity.controller;

import com.helpdesk.modules.identity.dto.CategoryDto;
import com.helpdesk.modules.identity.dto.TeamDto;
import com.helpdesk.modules.identity.service.ReferenceDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "1. Identity & Reference Data (UC00)", description = "Danh mục dùng chung: Category và Support Team")
public class ReferenceDataController {

    private final ReferenceDataService referenceDataService;

    public ReferenceDataController(ReferenceDataService referenceDataService) {
        this.referenceDataService = referenceDataService;
    }

    @GetMapping("/categories")
    @Operation(summary = "Danh sách danh mục yêu cầu (Public)")
    public ResponseEntity<List<CategoryDto>> listCategories(
            @RequestParam(defaultValue = "true") boolean activeOnly
    ) {
        return ResponseEntity.ok(referenceDataService.listCategories(activeOnly));
    }

    @GetMapping("/teams")
    @PreAuthorize("hasAnyRole('AGENT', 'SUPERVISOR', 'ADMIN')")
    @Operation(summary = "Danh sách nhóm hỗ trợ (Staff only)")
    public ResponseEntity<List<TeamDto>> listTeams() {
        return ResponseEntity.ok(referenceDataService.listTeams());
    }
}
