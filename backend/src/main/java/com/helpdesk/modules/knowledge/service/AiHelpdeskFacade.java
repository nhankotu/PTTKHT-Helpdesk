package com.helpdesk.modules.knowledge.service;

import com.helpdesk.common.exception.BusinessException;
import com.helpdesk.common.exception.ErrorCode;
import com.helpdesk.modules.knowledge.dto.KnowledgeDtos.AiCitationDto;
import com.helpdesk.modules.knowledge.dto.KnowledgeDtos.AiFeedbackCreateRequest;
import com.helpdesk.modules.knowledge.dto.KnowledgeDtos.AiFeedbackDto;
import com.helpdesk.modules.knowledge.dto.KnowledgeDtos.AiRunDto;
import com.helpdesk.modules.knowledge.dto.KnowledgeDtos.RagSuggestionRequest;
import com.helpdesk.modules.knowledge.entity.AiCitation;
import com.helpdesk.modules.knowledge.entity.AiFallbackReason;
import com.helpdesk.modules.knowledge.entity.AiRun;
import com.helpdesk.modules.knowledge.entity.AiRunOutcome;
import com.helpdesk.modules.knowledge.entity.AiSuggestionFeedback;
import com.helpdesk.modules.knowledge.entity.AiTaskType;
import com.helpdesk.modules.knowledge.entity.KbArticle;
import com.helpdesk.modules.knowledge.entity.KbChunk;
import com.helpdesk.modules.knowledge.entity.KbVersion;
import com.helpdesk.modules.knowledge.rag.RagGenerator;
import com.helpdesk.modules.knowledge.rag.RagGenerator.RagDraftResult;
import com.helpdesk.modules.knowledge.rag.RagGenerator.ScoredChunk;
import com.helpdesk.modules.knowledge.repository.AiCitationRepository;
import com.helpdesk.modules.knowledge.repository.AiRunRepository;
import com.helpdesk.modules.knowledge.repository.AiSuggestionFeedbackRepository;
import com.helpdesk.modules.knowledge.repository.KbArticleRepository;
import com.helpdesk.modules.knowledge.repository.KbChunkRepository;
import com.helpdesk.modules.knowledge.repository.KbVersionRepository;
import com.helpdesk.modules.ticket.service.TicketPublicContextPort;
import com.helpdesk.modules.ticket.service.TicketPublicContextPort.TicketPublicContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AiHelpdeskFacade {

    private static final double MIN_RELEVANCE = 0.15;
    private static final List<String> INJECTION_MARKERS = List.of(
            "ignore previous instructions",
            "ignore all instructions",
            "reveal system prompt",
            "show system prompt",
            "internal note",
            "bỏ qua hướng dẫn",
            "tiết lộ prompt",
            "ghi chú nội bộ"
    );

    private final TicketPublicContextPort ticketPublicContextPort;
    private final KbChunkRepository kbChunkRepository;
    private final KbVersionRepository kbVersionRepository;
    private final KbArticleRepository kbArticleRepository;
    private final AiRunRepository aiRunRepository;
    private final AiCitationRepository aiCitationRepository;
    private final AiSuggestionFeedbackRepository feedbackRepository;
    private final RagGenerator ragGenerator;
    private final Clock clock;

    public AiHelpdeskFacade(
            TicketPublicContextPort ticketPublicContextPort,
            KbChunkRepository kbChunkRepository,
            KbVersionRepository kbVersionRepository,
            KbArticleRepository kbArticleRepository,
            AiRunRepository aiRunRepository,
            AiCitationRepository aiCitationRepository,
            AiSuggestionFeedbackRepository feedbackRepository,
            RagGenerator ragGenerator,
            Clock clock
    ) {
        this.ticketPublicContextPort = ticketPublicContextPort;
        this.kbChunkRepository = kbChunkRepository;
        this.kbVersionRepository = kbVersionRepository;
        this.kbArticleRepository = kbArticleRepository;
        this.aiRunRepository = aiRunRepository;
        this.aiCitationRepository = aiCitationRepository;
        this.feedbackRepository = feedbackRepository;
        this.ragGenerator = ragGenerator;
        this.clock = clock;
    }

    @Transactional
    public AiRunDto generateRagSuggestion(Long ticketId, Long requestedBy, RagSuggestionRequest request) {
        long startNano = System.nanoTime();
        Instant now = Instant.now(clock);

        TicketPublicContext ctx = ticketPublicContextPort.requirePublicContext(ticketId);
        if (ctx.status().isTerminal()) {
            throw BusinessException.unprocessable(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    "Không thể tạo gợi ý AI cho ticket đã đóng hoặc hủy."
            );
        }

        String query = (request != null && request.customQuery() != null && !request.customQuery().isBlank())
                ? request.customQuery().trim()
                : buildPublicQuery(ctx);
        int topK = (request != null && request.topK() != null) ? request.topK() : 5;

        if (isPromptInjection(query)) {
            return saveFallback(ticketId, requestedBy, "guardrail", "rag-v1.0", query,
                    BigDecimal.ZERO, AiRunOutcome.FALLBACK_TO_AGENT, AiFallbackReason.OUT_OF_AUTO_SCOPE, startNano, now);
        }

        List<ScoredChunk> chunks = retrieveActivePublishedChunks(query, topK);
        if (chunks.isEmpty()) {
            return saveFallback(ticketId, requestedBy, "retriever", "rag-v1.0", query,
                    BigDecimal.ZERO, AiRunOutcome.FALLBACK_TO_AGENT, AiFallbackReason.NO_RELEVANT_KB, startNano, now);
        }

        RagDraftResult draft;
        try {
            draft = ragGenerator.generate(query, chunks);
        } catch (Exception ex) {
            return saveFallback(ticketId, requestedBy, "rag-generator", "rag-v1.0", query,
                    null, AiRunOutcome.FAILED, AiFallbackReason.AI_SERVICE_ERROR, startNano, now);
        }

        if (!draft.canAnswer() || draft.answerText() == null || draft.answerText().isBlank()) {
            return saveFallback(ticketId, requestedBy, draft.modelName(), draft.promptVersion(), query,
                    draft.confidence(), AiRunOutcome.FALLBACK_TO_AGENT, AiFallbackReason.LOW_CONFIDENCE, startNano, now);
        }

        Map<Long, ScoredChunk> byChunkId = chunks.stream()
                .collect(Collectors.toMap(sc -> sc.chunk().getId(), Function.identity(), (a, b) -> a));
        List<Long> citedIds = draft.citedChunkIds() == null ? List.of() : draft.citedChunkIds().stream().distinct().toList();

        boolean citationsValid = !citedIds.isEmpty() && citedIds.stream().allMatch(id -> {
            ScoredChunk sc = byChunkId.get(id);
            return sc != null && sc.chunk().isActive();
        });
        if (!citationsValid) {
            return saveFallback(ticketId, requestedBy, draft.modelName(), draft.promptVersion(), query,
                    draft.confidence(), AiRunOutcome.FALLBACK_TO_AGENT, AiFallbackReason.CITATION_INVALID, startNano, now);
        }

        AiRun savedRun = aiRunRepository.save(new AiRun(
                ticketId, requestedBy, AiTaskType.RAG_SUGGESTION, draft.modelName(), draft.promptVersion(),
                query, draft.answerText().trim(), draft.confidence(), AiRunOutcome.SUGGESTED, null, elapsedMs(startNano), now
        ));

        int order = 1;
        for (Long chunkId : citedIds) {
            ScoredChunk sc = byChunkId.get(chunkId);
            aiCitationRepository.save(new AiCitation(
                    savedRun.getId(), sc.chunk().getId(), sc.chunk().getVersionId(), sc.relevanceScore(), order++
            ));
        }
        return toAiRunDto(savedRun);
    }

    @Transactional(readOnly = true)
    public List<AiRunDto> listSuggestionsForTicket(Long ticketId) {
        return aiRunRepository.findByTicketIdOrderByCreatedAtDesc(ticketId).stream()
                .map(this::toAiRunDto)
                .toList();
    }

    @Transactional
    public AiFeedbackDto submitFeedback(Long aiRunId, Long agentId, AiFeedbackCreateRequest request) {
        if (!aiRunRepository.existsById(aiRunId)) {
            throw BusinessException.notFound("AiRun", aiRunId);
        }
        if (feedbackRepository.existsByAiRunId(aiRunId)) {
            throw BusinessException.conflict(ErrorCode.DUPLICATE_RESOURCE, "Gợi ý AI này đã được đánh giá.");
        }
        AiSuggestionFeedback saved = feedbackRepository.save(
                new AiSuggestionFeedback(aiRunId, agentId, request.decision(), request.note(), Instant.now(clock))
        );
        return toFeedbackDto(saved);
    }

    private AiRunDto saveFallback(
            Long ticketId, Long requestedBy, String model, String promptVer, String query,
            BigDecimal confidence, AiRunOutcome outcome, AiFallbackReason reason, long startNano, Instant now
    ) {
        AiRun run = aiRunRepository.save(new AiRun(
                ticketId, requestedBy, AiTaskType.RAG_SUGGESTION, model, promptVer,
                query, null, confidence, outcome, reason, elapsedMs(startNano), now
        ));
        return toAiRunDto(run);
    }

    private List<ScoredChunk> retrieveActivePublishedChunks(String query, int topK) {
        Set<String> queryTerms = tokenize(query);
        if (queryTerms.isEmpty()) {
            return List.of();
        }
        return kbChunkRepository.findAllActivePublishedChunks().stream()
                .map(chunk -> {
                    Set<String> chunkTerms = tokenize((chunk.getHeading() != null ? chunk.getHeading() + " " : "") + chunk.getContent());
                    long hits = queryTerms.stream().filter(chunkTerms::contains).count();
                    double score = (double) hits / (double) queryTerms.size();
                    return new ScoredChunk(chunk, BigDecimal.valueOf(Math.min(1.0, score)).setScale(4, RoundingMode.HALF_UP));
                })
                .filter(sc -> sc.relevanceScore().doubleValue() >= MIN_RELEVANCE)
                .sorted(Comparator.comparing(ScoredChunk::relevanceScore).reversed())
                .limit(topK)
                .toList();
    }

    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(w -> w.length() >= 2)
                .collect(Collectors.toSet());
    }

    private boolean isPromptInjection(String query) {
        String lower = query.toLowerCase(Locale.ROOT);
        return INJECTION_MARKERS.stream().anyMatch(lower::contains);
    }

    private String buildPublicQuery(TicketPublicContext ctx) {
        StringBuilder sb = new StringBuilder(ctx.subject());
        for (String body : ctx.publicMessageBodies()) {
            sb.append("\n").append(body);
        }
        return sb.toString().trim();
    }

    private int elapsedMs(long startNano) {
        return (int) Math.max(0, (System.nanoTime() - startNano) / 1_000_000L);
    }

    private AiRunDto toAiRunDto(AiRun run) {
        List<AiCitationDto> citations = aiCitationRepository.findByAiRunIdOrderByCitationOrderAsc(run.getId()).stream()
                .map(c -> {
                    KbChunk chunk = kbChunkRepository.findById(c.getKbChunkId()).orElse(null);
                    KbVersion ver = kbVersionRepository.findById(c.getKbVersionId()).orElse(null);
                    KbArticle art = ver != null ? kbArticleRepository.findById(ver.getArticleId()).orElse(null) : null;
                    String excerpt = (chunk != null && chunk.getContent().length() > 200)
                            ? chunk.getContent().substring(0, 200) + "..."
                            : (chunk != null ? chunk.getContent() : "");
                    return new AiCitationDto(
                            c.getId(), c.getKbChunkId(), c.getKbVersionId(),
                            art != null ? art.getId() : null,
                            art != null ? art.getArticleCode() : null,
                            ver != null ? ver.getTitle() : null,
                            chunk != null ? chunk.getHeading() : null,
                            excerpt, c.getRelevanceScore(), c.getCitationOrder()
                    );
                })
                .toList();

        AiFeedbackDto fb = feedbackRepository.findByAiRunId(run.getId()).map(this::toFeedbackDto).orElse(null);
        return new AiRunDto(
                run.getId(), run.getTicketId(), run.getTaskType(), run.getModelName(), run.getPromptVersion(),
                run.getOutcome(), run.getAnswerText(), run.getConfidence(), run.getFallbackReason(),
                run.getLatencyMs(), run.getCreatedAt(), citations, fb
        );
    }

    private AiFeedbackDto toFeedbackDto(AiSuggestionFeedback f) {
        return new AiFeedbackDto(f.getId(), f.getAiRunId(), f.getAgentId(), f.getDecision(), f.getNote(), f.getCreatedAt());
    }
}
