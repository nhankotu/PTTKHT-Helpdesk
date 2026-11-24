package com.helpdesk.modules.knowledge.service;

import com.helpdesk.modules.identity.service.IdentityDirectoryService;
import com.helpdesk.modules.knowledge.dto.KnowledgeDtos.AiRunDto;
import com.helpdesk.modules.knowledge.dto.KnowledgeDtos.KbRevokeResponse;
import com.helpdesk.modules.knowledge.entity.AiFallbackReason;
import com.helpdesk.modules.knowledge.entity.AiRun;
import com.helpdesk.modules.knowledge.entity.AiRunOutcome;
import com.helpdesk.modules.knowledge.entity.KbAccessScope;
import com.helpdesk.modules.knowledge.entity.KbArticle;
import com.helpdesk.modules.knowledge.entity.KbVersion;
import com.helpdesk.modules.knowledge.entity.KbVersionStatus;
import com.helpdesk.modules.knowledge.rag.GroundedRagGenerator;
import com.helpdesk.modules.knowledge.repository.AiCitationRepository;
import com.helpdesk.modules.knowledge.repository.AiRunRepository;
import com.helpdesk.modules.knowledge.repository.AiSuggestionFeedbackRepository;
import com.helpdesk.modules.knowledge.repository.KbArticleRepository;
import com.helpdesk.modules.knowledge.repository.KbChunkRepository;
import com.helpdesk.modules.knowledge.repository.KbVersionRepository;
import com.helpdesk.modules.ticket.entity.TicketStatus;
import com.helpdesk.modules.ticket.service.TicketPublicContextPort;
import com.helpdesk.modules.ticket.service.TicketPublicContextPort.TicketPublicContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseRevocationTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-11-22T01:30:00Z");

    @Mock
    private KbArticleRepository kbArticleRepository;
    @Mock
    private KbVersionRepository kbVersionRepository;
    @Mock
    private KbChunkRepository kbChunkRepository;
    @Mock
    private IdentityDirectoryService identityDirectoryService;
    @Mock
    private TicketPublicContextPort ticketPublicContextPort;
    @Mock
    private AiRunRepository aiRunRepository;
    @Mock
    private AiCitationRepository aiCitationRepository;
    @Mock
    private AiSuggestionFeedbackRepository feedbackRepository;

    private KnowledgeBaseService knowledgeBaseService;
    private AiHelpdeskFacade aiHelpdeskFacade;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        knowledgeBaseService = new KnowledgeBaseService(
                kbArticleRepository, kbVersionRepository, kbChunkRepository, identityDirectoryService, clock
        );
        aiHelpdeskFacade = new AiHelpdeskFacade(
                ticketPublicContextPort, kbChunkRepository, kbVersionRepository, kbArticleRepository,
                aiRunRepository, aiCitationRepository, feedbackRepository, new GroundedRagGenerator(), clock
        );
    }

    @Test
    @DisplayName("Ca #7: Thu hồi phiên bản KB (PUBLISHED -> ARCHIVED) vô hiệu hóa chunk ngay và RAG không còn dẫn nguồn")
    void revokeVersionDeactivatesChunksAndPreventsRagCitation() {
        KbArticle article = new KbArticle("KB-REFUND-01", 1L, KbAccessScope.PUBLIC, true, 5L, FIXED_NOW);
        ReflectionTestUtils.setField(article, "id", 30L);
        article.setCurrentPublishedVersionId(71L);

        KbVersion publishedVersion = new KbVersion(30L, 1, "Chính sách hoàn tiền", null, "Hoàn tiền trong 7 ngày", 5L, FIXED_NOW);
        ReflectionTestUtils.setField(publishedVersion, "id", 71L);
        publishedVersion.setStatus(KbVersionStatus.PUBLISHED);

        when(kbVersionRepository.findById(71L)).thenReturn(Optional.of(publishedVersion));
        when(kbArticleRepository.findById(30L)).thenReturn(Optional.of(article));
        when(kbChunkRepository.deactivateChunksByVersionId(71L)).thenReturn(4);

        KbRevokeResponse revokeRes = knowledgeBaseService.revokeVersion(71L, 9L, "Chính sách hoàn tiền đã thay đổi");

        assertThat(revokeRes.versionId()).isEqualTo(71L);
        assertThat(revokeRes.status()).isEqualTo(KbVersionStatus.ARCHIVED);
        assertThat(revokeRes.deactivatedChunks()).isEqualTo(4);
        assertThat(revokeRes.archivedAt()).isEqualTo(FIXED_NOW);
        assertThat(publishedVersion.getStatus()).isEqualTo(KbVersionStatus.ARCHIVED);
        assertThat(article.getCurrentPublishedVersionId()).isNull();
        verify(kbChunkRepository).deactivateChunksByVersionId(71L);

        // Ngay sau khi thu hồi, tập chunk active+PUBLISHED không còn chunk của version 71 -> RAG fallback
        when(ticketPublicContextPort.requirePublicContext(200L)).thenReturn(new TicketPublicContext(
                200L,
                "Hỏi về chính sách hoàn tiền",
                TicketStatus.IN_PROGRESS,
                List.of("Tôi muốn hoàn tiền trong 7 ngày thì làm thế nào?")
        ));
        when(kbChunkRepository.findAllActivePublishedChunks()).thenReturn(List.of());
        when(aiRunRepository.save(any(AiRun.class))).thenAnswer(inv -> {
            AiRun run = inv.getArgument(0);
            ReflectionTestUtils.setField(run, "id", 9001L);
            return run;
        });
        when(aiCitationRepository.findByAiRunIdOrderByCitationOrderAsc(9001L)).thenReturn(List.of());
        when(feedbackRepository.findByAiRunId(9001L)).thenReturn(Optional.empty());

        AiRunDto ragResult = aiHelpdeskFacade.generateRagSuggestion(200L, 15L, null);

        assertThat(ragResult.outcome()).isEqualTo(AiRunOutcome.FALLBACK_TO_AGENT);
        assertThat(ragResult.fallbackReason()).isEqualTo(AiFallbackReason.NO_RELEVANT_KB);
        assertThat(ragResult.citations()).isEmpty();
    }
}
