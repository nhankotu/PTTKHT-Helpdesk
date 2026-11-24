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
import com.helpdesk.modules.knowledge.rag.pipeline.RagContracts.*;
import com.helpdesk.modules.knowledge.rag.pipeline.RagPipeline;
import com.helpdesk.modules.knowledge.rag.pipeline.EvidenceAssembler;
import com.helpdesk.modules.knowledge.repository.RagRequestStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Propagation;
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
    private static final int DEFAULT_TOP_K = 10;
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
    private RagPipeline pipeline;
    private RagRequestStore requestStore;
    private EvidenceAssembler evidenceAssembler;
    private TransactionTemplate transactions;

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

    @Autowired
    public AiHelpdeskFacade(TicketPublicContextPort context, KbChunkRepository chunks, KbVersionRepository versions,
            KbArticleRepository articles, AiRunRepository runs, AiCitationRepository citations,
            AiSuggestionFeedbackRepository feedback, RagGenerator legacy, Clock clock, RagPipeline pipeline,
            RagRequestStore requests, EvidenceAssembler evidence, PlatformTransactionManager manager) {
        this(context, chunks, versions, articles, runs, citations, feedback, legacy, clock);
        this.pipeline = pipeline; this.requestStore = requests; this.evidenceAssembler = evidence;
        this.transactions = new TransactionTemplate(manager);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AiRunDto generateRagSuggestion(Long ticketId, Long requestedBy, RagSuggestionRequest request, String requestKey) {
        TicketPublicContext context = ticketPublicContextPort.requirePublicContext(ticketId);
        if (context.status().isTerminal()) throw BusinessException.unprocessable(ErrorCode.INVALID_STATE_TRANSITION, "Không thể tạo gợi ý cho ticket đã đóng.");
        String question = request != null && request.customQuery() != null && !request.customQuery().isBlank() ? request.customQuery().trim() : buildPublicQuery(context);
        var sources = new java.util.ArrayList<Source>();
        sources.add(new Source("question", question)); sources.add(new Source("subject", context.subject()));
        context.publicMessages().forEach(m -> sources.add(new Source(m.sourceId(), m.body())));
        Execution execution = pipeline.execute(new Input(ticketId, requestedBy, question, List.copyOf(sources)),
                request != null && request.topK() != null ? request.topK() : DEFAULT_TOP_K, requestKey);
        return transactions.execute(status -> {
            // Recheck ticket access before returning a cached answer or persisting a new draft.
            ticketPublicContextPort.requirePublicContext(ticketId);
            if (execution.cachedRunId() != null) {
                AiRun run = aiRunRepository.findById(execution.cachedRunId()).orElseThrow(() -> BusinessException.notFound("AiRun", execution.cachedRunId()));
                if (!ticketId.equals(run.getTicketId()) || !requestedBy.equals(run.getRequestedBy())) throw BusinessException.forbidden(ErrorCode.TICKET_ACCESS_DENIED, "Sai phạm vi request AI.");
                if (actionable(run) && !runSourcesValid(run)) throw BusinessException.conflict(ErrorCode.INVALID_STATE_TRANSITION, "Nguồn của gợi ý đã thay đổi/thu hồi; không dùng lại bản nháp này.");
                return toAiRunDto(run);
            }
            Draft draft = execution.draft();
            if (!execution.context().isEmpty() && !evidenceAssembler.lockCurrent(execution.context()))
                draft = Draft.stop(Decision.FALLBACK, "OUTDATED_OR_REVOKED_KB");
            AiRunOutcome outcome = switch (draft.decision()) {
                case ANSWER -> AiRunOutcome.SUGGESTED;
                case ASK_CLARIFICATION -> AiRunOutcome.CLARIFICATION_REQUIRED;
                case FAILED -> AiRunOutcome.FAILED;
                default -> AiRunOutcome.FALLBACK_TO_AGENT;
            };
            AiFallbackReason reason = draft.reason() == null ? null : AiFallbackReason.valueOf(draft.reason());
            String storedQuery=question.length()>8000 ? "[INPUT_LIMIT_EXCEEDED sha256="+com.helpdesk.modules.knowledge.rag.pipeline.RagGuards.hash(question)+" chars="+question.length()+"]" : question;
            AiRun saved = aiRunRepository.save(new AiRun(ticketId, requestedBy, AiTaskType.RAG_SUGGESTION,
                    execution.modelName(), "two-stage-v1", storedQuery, draft.text(), draft.confidence(), outcome, reason, execution.latencyMs(), Instant.now(clock)));
            int order = 1;
            for (Evidence citation : draft.citations()) {
                var parts=draft.parts().stream().filter(p -> p.supports().stream().anyMatch(s -> s.chunkId()==citation.chunkId())).toList();
                String quotes=parts.stream().flatMap(p -> p.supports().stream()).filter(s -> s.chunkId()==citation.chunkId()).map(Support::quote).distinct().collect(Collectors.joining("\n\n"));
                aiCitationRepository.save(new AiCitation(saved.getId(), citation.chunkId(), citation.versionId(), citation.score(), order++, quotes,
                        jsonString(parts.stream().map(AnswerPart::intentId).distinct().toList())));
            }
            String stages;
            try { stages = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(execution.audit()); }
            catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException("Cannot encode RAG stage metadata"); }
            requestStore.complete(requestedBy, execution.requestKey(), saved.getId(), execution.modelName(), draft.reason(), execution.latencyMs(),
                    stages, execution.context().isEmpty() ? null : evidenceAssembler.fingerprint(execution.context()));
            return toAiRunDto(saved);
        });
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AiRunDto generateRagSuggestion(Long ticketId, Long requestedBy, RagSuggestionRequest request) {
        if (pipeline != null) throw BusinessException.unprocessable(ErrorCode.MISSING_HEADER,"Cần Idempotency-Key từ client.");
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
        int topK = (request != null && request.topK() != null) ? request.topK() : DEFAULT_TOP_K;

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
        ticketPublicContextPort.requirePublicContext(ticketId);
        return aiRunRepository.findByTicketIdOrderByCreatedAtDesc(ticketId).stream()
                .map(this::toAiRunDto)
                .toList();
    }

    @Transactional
    public AiFeedbackDto submitFeedback(Long aiRunId, Long agentId, AiFeedbackCreateRequest request) {
        if (!aiRunRepository.existsById(aiRunId)) {
            throw BusinessException.notFound("AiRun", aiRunId);
        }
        AiRun run=aiRunRepository.findById(aiRunId).orElseThrow();
        ticketPublicContextPort.requirePublicContext(run.getTicketId());
        if(pipeline!=null && actionable(run) && !runSourcesValid(run)) throw BusinessException.conflict(ErrorCode.INVALID_STATE_TRANSITION,"Nguồn đã đổi/thu hồi; không chấp nhận bản nháp cũ.");
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
        if(pipeline!=null && actionable(run) && !runSourcesValid(run)) return new AiRunDto(run.getId(),run.getTicketId(),run.getTaskType(),run.getModelName(),run.getPromptVersion(),
                AiRunOutcome.FALLBACK_TO_AGENT,null,BigDecimal.ZERO,AiFallbackReason.OUTDATED_OR_REVOKED_KB,run.getLatencyMs(),run.getCreatedAt(),List.of(),null);
        List<AiCitationDto> citations = aiCitationRepository.findByAiRunIdOrderByCitationOrderAsc(run.getId()).stream()
                .map(c -> {
                    KbChunk chunk = kbChunkRepository.findById(c.getKbChunkId()).orElse(null);
                    KbVersion ver = kbVersionRepository.findById(c.getKbVersionId()).orElse(null);
                    KbArticle art = ver != null ? kbArticleRepository.findById(ver.getArticleId()).orElse(null) : null;
                    String excerpt = c.getQuotedText()!=null ? c.getQuotedText() : (chunk != null && chunk.getContent().length() > 200)
                            ? chunk.getContent().substring(0, 200) + "..."
                            : (chunk != null ? chunk.getContent() : "");
                    return new AiCitationDto(
                            c.getId(), c.getKbChunkId(), c.getKbVersionId(),
                            art != null ? art.getId() : null,
                            art != null ? art.getArticleCode() : null,
                            ver != null ? ver.getTitle() : null,
                            chunk != null ? chunk.getHeading() : null,
                            excerpt, c.getRelevanceScore(), c.getCitationOrder(), intentIds(c.getIntentIds())
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
    private boolean actionable(AiRun run) { return run.getOutcome()==AiRunOutcome.SUGGESTED || run.getOutcome()==AiRunOutcome.CLARIFICATION_REQUIRED; }
    private boolean runSourcesValid(AiRun run) {
        var state=requestStore.sources(run.getId());
        if(state==null) return false;
        if(state.ids().isEmpty()) return run.getOutcome()==AiRunOutcome.CLARIFICATION_REQUIRED && state.fingerprint()==null;
        return evidenceAssembler.cachedCurrent(state.ids(),state.fingerprint());
    }
    private String jsonString(Object value) {
        try { return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value); }
        catch(com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException("Cannot encode RAG audit"); }
    }
    private List<String> intentIds(String value) {
        if(value==null) return List.of();
        try { return new com.fasterxml.jackson.databind.ObjectMapper().readValue(value,new com.fasterxml.jackson.core.type.TypeReference<List<String>>(){}); }
        catch(Exception ex) { return List.of(); }
    }

    private AiFeedbackDto toFeedbackDto(AiSuggestionFeedback f) {
        return new AiFeedbackDto(f.getId(), f.getAiRunId(), f.getAgentId(), f.getDecision(), f.getNote(), f.getCreatedAt());
    }
}
