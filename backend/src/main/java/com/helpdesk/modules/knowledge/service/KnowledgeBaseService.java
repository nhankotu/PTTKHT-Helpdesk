package com.helpdesk.modules.knowledge.service;

import com.helpdesk.common.dto.PageResponse;
import com.helpdesk.common.exception.BusinessException;
import com.helpdesk.common.exception.ErrorCode;
import com.helpdesk.modules.identity.service.IdentityDirectoryService;
import com.helpdesk.modules.knowledge.dto.KnowledgeDtos.KbArticleCreateRequest;
import com.helpdesk.modules.knowledge.dto.KnowledgeDtos.KbArticleDetailDto;
import com.helpdesk.modules.knowledge.dto.KnowledgeDtos.KbChunkDto;
import com.helpdesk.modules.knowledge.dto.KnowledgeDtos.KbRejectRequest;
import com.helpdesk.modules.knowledge.dto.KnowledgeDtos.KbRevokeResponse;
import com.helpdesk.modules.knowledge.dto.KnowledgeDtos.KbVersionCreateOrUpdateRequest;
import com.helpdesk.modules.knowledge.dto.KnowledgeDtos.KbVersionDto;
import com.helpdesk.modules.knowledge.entity.KbAccessScope;
import com.helpdesk.modules.knowledge.entity.KbArticle;
import com.helpdesk.modules.knowledge.entity.KbChunk;
import com.helpdesk.modules.knowledge.entity.KbVersion;
import com.helpdesk.modules.knowledge.entity.KbVersionStatus;
import com.helpdesk.modules.knowledge.repository.KbArticleRepository;
import com.helpdesk.modules.knowledge.repository.KbChunkRepository;
import com.helpdesk.modules.knowledge.repository.KbVersionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class KnowledgeBaseService {

    private final KbArticleRepository kbArticleRepository;
    private final KbVersionRepository kbVersionRepository;
    private final KbChunkRepository kbChunkRepository;
    private final IdentityDirectoryService identityDirectoryService;
    private final Clock clock;

    public KnowledgeBaseService(
            KbArticleRepository kbArticleRepository,
            KbVersionRepository kbVersionRepository,
            KbChunkRepository kbChunkRepository,
            IdentityDirectoryService identityDirectoryService,
            Clock clock
    ) {
        this.kbArticleRepository = kbArticleRepository;
        this.kbVersionRepository = kbVersionRepository;
        this.kbChunkRepository = kbChunkRepository;
        this.identityDirectoryService = identityDirectoryService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<KbArticleDetailDto> listPublicPublishedArticles(Pageable pageable) {
        Page<KbArticle> page = kbArticleRepository
                .findByAccessScopeAndCurrentPublishedVersionIdIsNotNullOrderByUpdatedAtDesc(KbAccessScope.PUBLIC, pageable);
        return PageResponse.from(page.map(article -> {
            List<KbVersionDto> published = kbVersionRepository
                    .findByArticleIdAndStatus(article.getId(), KbVersionStatus.PUBLISHED)
                    .map(v -> List.of(toVersionDto(v)))
                    .orElse(List.of());
            return toArticleDto(article, published);
        }));
    }

    @Transactional(readOnly = true)
    public PageResponse<KbArticleDetailDto> listAllArticles(Pageable pageable) {
        return PageResponse.from(kbArticleRepository.findAllByOrderByUpdatedAtDesc(pageable)
                .map(article -> toArticleDto(
                        article,
                        kbVersionRepository.findByArticleIdOrderByVersionNumberDesc(article.getId())
                                .stream().map(this::toVersionDto).toList()
                )));
    }

    @Transactional(readOnly = true)
    public KbArticleDetailDto getArticleDetail(Long articleId) {
        KbArticle article = kbArticleRepository.findById(articleId)
                .orElseThrow(() -> BusinessException.notFound("KbArticle", articleId));
        List<KbVersionDto> versions = kbVersionRepository.findByArticleIdOrderByVersionNumberDesc(articleId)
                .stream().map(this::toVersionDto).toList();
        return toArticleDto(article, versions);
    }

    @Transactional
    public KbArticleDetailDto createArticle(Long creatorId, KbArticleCreateRequest request) {
        String code = request.articleCode().trim().toUpperCase();
        if (kbArticleRepository.existsByArticleCode(code)) {
            throw BusinessException.conflict(ErrorCode.DUPLICATE_RESOURCE, "Mã bài viết KB đã tồn tại: " + code);
        }
        if (request.categoryId() != null) {
            identityDirectoryService.requireCategoryById(request.categoryId());
        }
        Instant now = Instant.now(clock);
        KbArticle article = kbArticleRepository.save(new KbArticle(
                code, request.categoryId(), request.accessScope(), request.allowAutoReply(), creatorId, now
        ));
        KbVersion v1 = kbVersionRepository.save(new KbVersion(
                article.getId(), 1, request.title().trim(), request.summary(), request.content().trim(), creatorId, now
        ));
        return toArticleDto(article, List.of(toVersionDto(v1)));
    }

    @Transactional
    public KbVersionDto createNewVersion(Long articleId, Long creatorId, KbVersionCreateOrUpdateRequest request) {
        if (!kbArticleRepository.existsById(articleId)) {
            throw BusinessException.notFound("KbArticle", articleId);
        }
        int nextNumber = kbVersionRepository.findFirstByArticleIdOrderByVersionNumberDesc(articleId)
                .map(v -> v.getVersionNumber() + 1)
                .orElse(1);
        KbVersion saved = kbVersionRepository.save(new KbVersion(
                articleId, nextNumber, request.title().trim(), request.summary(),
                request.content().trim(), creatorId, Instant.now(clock)
        ));
        return toVersionDto(saved);
    }

    @Transactional
    public KbVersionDto updateDraftVersion(Long versionId, KbVersionCreateOrUpdateRequest request) {
        KbVersion version = requireVersion(versionId);
        if (version.getStatus() != KbVersionStatus.DRAFT && version.getStatus() != KbVersionStatus.REJECTED) {
            throw BusinessException.unprocessable(
                    ErrorCode.KB_VERSION_NOT_EDITABLE,
                    "Chỉ được sửa phiên bản ở trạng thái DRAFT hoặc REJECTED."
            );
        }
        version.setTitle(request.title().trim());
        version.setSummary(request.summary());
        version.setContent(request.content().trim());
        return toVersionDto(kbVersionRepository.save(version));
    }

    @Transactional
    public KbVersionDto submitForReview(Long versionId) {
        KbVersion version = requireVersion(versionId);
        if (version.getStatus() != KbVersionStatus.DRAFT && version.getStatus() != KbVersionStatus.REJECTED) {
            throw BusinessException.unprocessable(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    "Chỉ gửi duyệt được phiên bản ở trạng thái DRAFT hoặc REJECTED."
            );
        }
        version.setStatus(KbVersionStatus.IN_REVIEW);
        return toVersionDto(kbVersionRepository.save(version));
    }

    @Transactional
    public KbVersionDto publishVersion(Long versionId, Long reviewerId) {
        KbVersion version = requireVersion(versionId);
        if (version.getStatus() != KbVersionStatus.IN_REVIEW) {
            throw BusinessException.unprocessable(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    "Chỉ xuất bản được phiên bản đang ở trạng thái IN_REVIEW."
            );
        }
        Instant now = Instant.now(clock);
        KbArticle article = kbArticleRepository.findById(version.getArticleId())
                .orElseThrow(() -> BusinessException.notFound("KbArticle", version.getArticleId()));

        kbVersionRepository.findByArticleIdAndStatus(article.getId(), KbVersionStatus.PUBLISHED).ifPresent(previous -> {
            previous.setStatus(KbVersionStatus.ARCHIVED);
            previous.setArchivedAt(now);
            kbVersionRepository.saveAndFlush(previous);
            kbChunkRepository.deactivateChunksByVersionId(previous.getId());
        });

        version.setStatus(KbVersionStatus.PUBLISHED);
        version.setReviewedBy(reviewerId);
        version.setPublishedAt(now);
        KbVersion saved = kbVersionRepository.saveAndFlush(version);

        article.setCurrentPublishedVersionId(saved.getId());
        kbArticleRepository.save(article);

        buildAndSaveChunks(saved, now);
        return toVersionDto(saved);
    }

    @Transactional
    public KbVersionDto rejectVersion(Long versionId, Long reviewerId, KbRejectRequest request) {
        KbVersion version = requireVersion(versionId);
        if (version.getStatus() != KbVersionStatus.IN_REVIEW) {
            throw BusinessException.unprocessable(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    "Chỉ từ chối được phiên bản đang ở trạng thái IN_REVIEW."
            );
        }
        version.setStatus(KbVersionStatus.REJECTED);
        version.setReviewedBy(reviewerId);
        version.setReviewNote(request.reviewNote().trim());
        return toVersionDto(kbVersionRepository.save(version));
    }

    @Transactional
    public KbRevokeResponse revokeVersion(Long versionId, Long reviewerId, String reason) {
        KbVersion version = requireVersion(versionId);
        if (version.getStatus() != KbVersionStatus.PUBLISHED) {
            throw BusinessException.unprocessable(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    "Chỉ thu hồi được phiên bản đang ở trạng thái PUBLISHED."
            );
        }
        Instant now = Instant.now(clock);
        version.setStatus(KbVersionStatus.ARCHIVED);
        version.setArchivedAt(now);
        version.setReviewedBy(reviewerId);
        if (reason != null && !reason.isBlank()) {
            version.setReviewNote(reason.trim());
        }
        kbVersionRepository.save(version);

        kbArticleRepository.findById(version.getArticleId()).ifPresent(article -> {
            if (version.getId().equals(article.getCurrentPublishedVersionId())) {
                article.setCurrentPublishedVersionId(null);
                kbArticleRepository.save(article);
            }
        });

        int deactivatedCount = kbChunkRepository.deactivateChunksByVersionId(version.getId());
        return new KbRevokeResponse(version.getId(), KbVersionStatus.ARCHIVED, deactivatedCount, now);
    }

    @Transactional(readOnly = true)
    public KbChunkDto getChunkPreview(Long chunkId) {
        KbChunk chunk = kbChunkRepository.findById(chunkId)
                .orElseThrow(() -> BusinessException.notFound("KbChunk", chunkId));
        return new KbChunkDto(
                chunk.getId(), chunk.getVersionId(), chunk.getChunkIndex(),
                chunk.getHeading(), chunk.getContent(), chunk.getTokenCount(), chunk.isActive()
        );
    }

    private void buildAndSaveChunks(KbVersion version, Instant now) {
        String[] blocks = version.getContent().split("\\r?\\n\\s*\\r?\\n");
        List<KbChunk> chunks = new ArrayList<>();
        int index = 0;
        for (String block : blocks) {
            String trimmed = block.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String heading = version.getTitle();
            if (trimmed.startsWith("#")) {
                int breakIdx = trimmed.indexOf('\n');
                heading = (breakIdx > 0 ? trimmed.substring(0, breakIdx) : trimmed).replaceAll("^#+\\s*", "").trim();
            }
            int tokenEstimate = Math.max(1, trimmed.split("\\s+").length);
            chunks.add(new KbChunk(version.getId(), index++, heading, trimmed, tokenEstimate, now));
        }
        if (chunks.isEmpty()) {
            chunks.add(new KbChunk(version.getId(), 0, version.getTitle(), version.getContent(), 1, now));
        }
        kbChunkRepository.saveAll(chunks);
    }

    private KbVersion requireVersion(Long versionId) {
        return kbVersionRepository.findById(versionId)
                .orElseThrow(() -> BusinessException.notFound("KbVersion", versionId));
    }

    private KbArticleDetailDto toArticleDto(KbArticle a, List<KbVersionDto> versions) {
        return new KbArticleDetailDto(
                a.getId(), a.getArticleCode(), a.getCategoryId(), a.getAccessScope(),
                a.isAllowAutoReply(), a.getCurrentPublishedVersionId(), a.getCreatedAt(), a.getUpdatedAt(), versions
        );
    }

    private KbVersionDto toVersionDto(KbVersion v) {
        return new KbVersionDto(
                v.getId(), v.getArticleId(), v.getVersionNumber(), v.getTitle(), v.getSummary(),
                v.getContent(), v.getStatus(), v.getCreatedBy(), v.getReviewedBy(),
                v.getReviewNote(), v.getPublishedAt(), v.getArchivedAt(), v.getCreatedAt()
        );
    }
}
