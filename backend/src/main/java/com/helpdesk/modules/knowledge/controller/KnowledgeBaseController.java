package com.helpdesk.modules.knowledge.controller;

import com.helpdesk.common.dto.PageResponse;
import com.helpdesk.common.security.SecurityUtils;
import com.helpdesk.modules.knowledge.dto.KnowledgeDtos.KbArticleCreateRequest;
import com.helpdesk.modules.knowledge.dto.KnowledgeDtos.KbArticleDetailDto;
import com.helpdesk.modules.knowledge.dto.KnowledgeDtos.KbChunkDto;
import com.helpdesk.modules.knowledge.dto.KnowledgeDtos.KbRejectRequest;
import com.helpdesk.modules.knowledge.dto.KnowledgeDtos.KbRevokeRequest;
import com.helpdesk.modules.knowledge.dto.KnowledgeDtos.KbRevokeResponse;
import com.helpdesk.modules.knowledge.dto.KnowledgeDtos.KbVersionCreateOrUpdateRequest;
import com.helpdesk.modules.knowledge.dto.KnowledgeDtos.KbVersionDto;
import com.helpdesk.modules.knowledge.service.KnowledgeBaseService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "6A. Knowledge Base (UC06)", description = "Quản lý bài viết KB, quy trình duyệt phiên bản, xuất bản, thu hồi và tra cứu công khai")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @GetMapping("/customer/kb/articles")
    @Operation(summary = "Tra cứu bài KB công khai (access_scope=PUBLIC & đang PUBLISHED)")
    public ResponseEntity<PageResponse<KbArticleDetailDto>> listPublicArticles(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(knowledgeBaseService.listPublicPublishedArticles(pageable));
    }

    @GetMapping("/kb/articles")
    @PreAuthorize("hasAnyRole('AGENT', 'SUPERVISOR', 'ADMIN')")
    @Operation(summary = "Danh sách tất cả bài viết KB kèm lịch sử phiên bản")
    public ResponseEntity<PageResponse<KbArticleDetailDto>> listAllArticles(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(knowledgeBaseService.listAllArticles(pageable));
    }

    @GetMapping("/kb/articles/{articleId}")
    @PreAuthorize("hasAnyRole('AGENT', 'SUPERVISOR', 'ADMIN')")
    @Operation(summary = "Xem chi tiết bài viết KB kèm toàn bộ lịch sử phiên bản")
    public ResponseEntity<KbArticleDetailDto> getArticleDetail(@PathVariable Long articleId) {
        return ResponseEntity.ok(knowledgeBaseService.getArticleDetail(articleId));
    }

    @PostMapping("/kb/articles")
    @PreAuthorize("hasAnyRole('AGENT', 'SUPERVISOR', 'ADMIN')")
    @Operation(summary = "Tạo bài viết KB mới kèm phiên bản v1 ở trạng thái DRAFT")
    public ResponseEntity<KbArticleDetailDto> createArticle(@Valid @RequestBody KbArticleCreateRequest request) {
        Long userId = SecurityUtils.requireCurrentPrincipal().userId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(knowledgeBaseService.createArticle(userId, request));
    }

    @PostMapping("/kb/articles/{articleId}/versions")
    @PreAuthorize("hasAnyRole('AGENT', 'SUPERVISOR', 'ADMIN')")
    @Operation(summary = "Tạo phiên bản DRAFT mới cho bài viết KB (không ghi đè bản PUBLISHED)")
    public ResponseEntity<KbVersionDto> createNewVersion(
            @PathVariable Long articleId,
            @Valid @RequestBody KbVersionCreateOrUpdateRequest request
    ) {
        Long userId = SecurityUtils.requireCurrentPrincipal().userId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(knowledgeBaseService.createNewVersion(articleId, userId, request));
    }

    @PutMapping("/kb/versions/{versionId}")
    @PreAuthorize("hasAnyRole('AGENT', 'SUPERVISOR', 'ADMIN')")
    @Operation(summary = "Cập nhật nội dung phiên bản KB khi đang ở trạng thái DRAFT hoặc REJECTED")
    public ResponseEntity<KbVersionDto> updateDraftVersion(
            @PathVariable Long versionId,
            @Valid @RequestBody KbVersionCreateOrUpdateRequest request
    ) {
        return ResponseEntity.ok(knowledgeBaseService.updateDraftVersion(versionId, request));
    }

    @PostMapping("/kb/versions/{versionId}/submit")
    @PreAuthorize("hasAnyRole('AGENT', 'SUPERVISOR', 'ADMIN')")
    @Operation(summary = "Gửi duyệt phiên bản KB (DRAFT/REJECTED -> IN_REVIEW)")
    public ResponseEntity<KbVersionDto> submitForReview(@PathVariable Long versionId) {
        return ResponseEntity.ok(knowledgeBaseService.submitForReview(versionId));
    }

    @PostMapping("/kb/versions/{versionId}/publish")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    @Operation(summary = "Phê duyệt và xuất bản phiên bản KB (IN_REVIEW -> PUBLISHED), tự archive bản cũ và sinh kb_chunk")
    public ResponseEntity<KbVersionDto> publishVersion(@PathVariable Long versionId) {
        Long reviewerId = SecurityUtils.requireCurrentPrincipal().userId();
        return ResponseEntity.ok(knowledgeBaseService.publishVersion(versionId, reviewerId));
    }

    @PostMapping("/kb/versions/{versionId}/reject")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    @Operation(summary = "Từ chối duyệt phiên bản KB (IN_REVIEW -> REJECTED, bắt buộc reviewNote)")
    public ResponseEntity<KbVersionDto> rejectVersion(
            @PathVariable Long versionId,
            @Valid @RequestBody KbRejectRequest request
    ) {
        Long reviewerId = SecurityUtils.requireCurrentPrincipal().userId();
        return ResponseEntity.ok(knowledgeBaseService.rejectVersion(versionId, reviewerId, request));
    }

    @PostMapping("/kb/versions/{versionId}/revoke")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    @Operation(summary = "Thu hồi phiên bản KB (PUBLISHED -> ARCHIVED), vô hiệu hóa toàn bộ kb_chunk ngay lập tức")
    public ResponseEntity<KbRevokeResponse> revokeVersion(
            @PathVariable Long versionId,
            @Valid @RequestBody(required = false) KbRevokeRequest request
    ) {
        Long reviewerId = SecurityUtils.requireCurrentPrincipal().userId();
        String reason = request != null ? request.reason() : null;
        return ResponseEntity.ok(knowledgeBaseService.revokeVersion(versionId, reviewerId, reason));
    }

    @GetMapping("/kb/chunks/{chunkId}")
    @PreAuthorize("hasAnyRole('AGENT', 'SUPERVISOR', 'ADMIN')")
    @Operation(summary = "Xem chi tiết một đoạn kb_chunk phục vụ kiểm tra trích dẫn AI")
    public ResponseEntity<KbChunkDto> getChunkPreview(@PathVariable Long chunkId) {
        return ResponseEntity.ok(knowledgeBaseService.getChunkPreview(chunkId));
    }
}
