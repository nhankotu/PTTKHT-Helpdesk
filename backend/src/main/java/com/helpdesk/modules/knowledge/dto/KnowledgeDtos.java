package com.helpdesk.modules.knowledge.dto;

import com.helpdesk.modules.knowledge.entity.AiFallbackReason;
import com.helpdesk.modules.knowledge.entity.AiFeedbackDecision;
import com.helpdesk.modules.knowledge.entity.AiRunOutcome;
import com.helpdesk.modules.knowledge.entity.AiTaskType;
import com.helpdesk.modules.knowledge.entity.KbAccessScope;
import com.helpdesk.modules.knowledge.entity.KbVersionStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class KnowledgeDtos {

    private KnowledgeDtos() {
    }

    public record KbArticleCreateRequest(
            @NotBlank @Size(max = 30) String articleCode,
            Long categoryId,
            @NotNull KbAccessScope accessScope,
            boolean allowAutoReply,
            @NotBlank @Size(max = 250) String title,
            @Size(max = 2000) String summary,
            @NotBlank String content
    ) {
    }

    public record KbVersionCreateOrUpdateRequest(
            @NotBlank @Size(max = 250) String title,
            @Size(max = 2000) String summary,
            @NotBlank String content
    ) {
    }

    public record KbRejectRequest(
            @NotBlank @Size(max = 2000) String reviewNote
    ) {
    }

    public record KbRevokeRequest(
            @Size(max = 1000) String reason
    ) {
    }

    public record KbRevokeResponse(
            Long versionId,
            KbVersionStatus status,
            int deactivatedChunks,
            Instant archivedAt
    ) {
    }

    public record KbVersionDto(
            Long id,
            Long articleId,
            int versionNumber,
            String title,
            String summary,
            String content,
            KbVersionStatus status,
            Long createdBy,
            Long reviewedBy,
            String reviewNote,
            Instant publishedAt,
            Instant archivedAt,
            Instant createdAt
    ) {
    }

    public record KbArticleDetailDto(
            Long id,
            String articleCode,
            Long categoryId,
            KbAccessScope accessScope,
            boolean allowAutoReply,
            Long currentPublishedVersionId,
            Instant createdAt,
            Instant updatedAt,
            List<KbVersionDto> versions
    ) {
    }

    public record KbChunkDto(
            Long id,
            Long versionId,
            int chunkIndex,
            String heading,
            String content,
            int tokenCount,
            boolean isActive
    ) {
    }

    public record RagSuggestionRequest(
            @Size(max = 2000) String customQuery,
            @Min(1) @Max(10) Integer topK
    ) {
    }

    public record AiCitationDto(
            Long id,
            Long kbChunkId,
            Long kbVersionId,
            Long articleId,
            String articleCode,
            String articleTitle,
            String heading,
            String excerpt,
            BigDecimal relevanceScore,
            int citationOrder,
            List<String> intentIds
    ) {
        public AiCitationDto(Long id,Long chunkId,Long versionId,Long articleId,String articleCode,String title,String heading,String excerpt,BigDecimal score,int order) {
            this(id,chunkId,versionId,articleId,articleCode,title,heading,excerpt,score,order,List.of());
        }
    }

    public record AiFeedbackDto(
            Long id,
            Long aiRunId,
            Long agentId,
            AiFeedbackDecision decision,
            String note,
            Instant createdAt
    ) {
    }

    public record AiFeedbackCreateRequest(
            @NotNull AiFeedbackDecision decision,
            @Size(max = 2000) String note
    ) {
    }

    public record AiRunDto(
            Long id,
            Long ticketId,
            AiTaskType taskType,
            String modelName,
            String promptVersion,
            AiRunOutcome outcome,
            String answerText,
            BigDecimal confidence,
            AiFallbackReason fallbackReason,
            Integer latencyMs,
            Instant createdAt,
            List<AiCitationDto> citations,
            AiFeedbackDto feedback
    ) {
    }
}
