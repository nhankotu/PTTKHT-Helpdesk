package com.helpdesk.modules.knowledge.rag.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;

public final class RagContracts {
    private RagContracts() {}
    public enum Decision { READY, ANSWER, ASK_CLARIFICATION, FALLBACK, OUT_OF_SCOPE, FAILED }
    public enum IntentCode { CANCEL_ORDER, SHIPPING_FEE, RETURN_PACKAGING, RETURN_SHIPPING, ONLINE_RETURN, GIFT_PREPAYMENT, OTHER_POLICY }
    public record Source(String id, String text) {}
    public record Usage(String provider, String model, long latencyMs, Long inputTokens, Long outputTokens) {}
    public static final class Trace {
        private final java.util.Map<String, Usage> stages = new java.util.LinkedHashMap<>();
        private Plan validatedPlan;
        private java.util.List<java.util.Map<String, String>> sourceHashes = List.of();
        private List<Evidence> context = List.of();
        public synchronized void record(String stage, Usage usage) { stages.put(stage, usage); }
        public synchronized java.util.Map<String, Usage> snapshot() { return java.util.Map.copyOf(stages); }
        public synchronized void context(List<Evidence> context) { this.context = List.copyOf(context); }
        public synchronized void plan(Plan plan, List<Source> sources) {
            this.validatedPlan = plan;
            sourceHashes = sources.stream().map(s -> java.util.Map.of("sourceId", s.id(), "sha256", RagGuards.hash(s.text()))).toList();
        }
        public synchronized java.util.Map<String, Object> audit() {
            var result = new java.util.LinkedHashMap<String, Object>(); result.put("stages", snapshot());
            if (validatedPlan != null) { result.put("queryPlan", validatedPlan); result.put("sources", sourceHashes); }
            result.put("context", context.stream().map(e -> java.util.Map.of("chunkId", e.chunkId(), "versionId", e.versionId(), "versionHash", e.versionHash(), "chunkHash", RagGuards.hash(e.content()))).toList());
            return java.util.Map.copyOf(result);
        }
    }
    public record Input(Long ticketId, Long actorId, String question, List<Source> sources, long deadlineNanos, Trace trace) {
        public Input(Long ticketId, Long actorId, String question, List<Source> sources) { this(ticketId, actorId, question, sources, System.nanoTime() + 25_000_000_000L, new Trace()); }
        public int remainingMillis(int maximum) {
            long remaining = (deadlineNanos - System.nanoTime()) / 1_000_000;
            if (remaining < 100) throw new GateFailure("REQUEST_DEADLINE_EXCEEDED");
            return (int) Math.min(maximum, remaining);
        }
    }
    public record Fact(String id, String field, String value, String sourceId, int start, int end) {}
    public record Intent(String id, IntentCode code, String query, List<String> factRefs) {}
    public record Plan(Decision decision, String normalizedQuestion, List<Intent> intents,
                       List<Fact> facts, List<String> missingFields, List<String> ambiguities, BigDecimal confidence) {}
    public record Evidence(long chunkId, long versionId, String articleCode, String versionHash,
                           int chunkIndex, String heading, String content, BigDecimal score) {}
    public record Support(long chunkId, long versionId, String quote) {}
    public record AnswerPart(String intentId, List<Support> supports) {}
    public record Draft(Decision decision, String text, List<Evidence> citations, BigDecimal confidence, String reason, List<AnswerPart> parts) {
        public Draft(Decision decision, String text, List<Evidence> citations, BigDecimal confidence, String reason) { this(decision, text, citations, confidence, reason, List.of()); }
        public static Draft stop(Decision decision, String reason) { return new Draft(decision, null, List.of(), BigDecimal.ZERO, reason); }
    }
    public record Execution(String requestKey, Draft draft, Long cachedRunId, String modelName, int latencyMs, java.util.Map<String, Object> audit, List<Evidence> context) {
        public Execution(String requestKey, Draft draft, Long cachedRunId, String modelName, int latencyMs) { this(requestKey, draft, cachedRunId, modelName, latencyMs, java.util.Map.of(), List.of()); }
    }
    public static class GateFailure extends RuntimeException {
        private final String reason;
        public GateFailure(String reason) { super(reason); this.reason = reason; }
        public String reason() { return reason; }
    }
    public interface QueryPreprocessor { JsonNode prepare(Input input); default String modelName() { return "query-v1"; } }
    public interface GroundedAnswerer { JsonNode answer(Input input, Plan plan, List<Evidence> evidence); default String modelName() { return "answer-v1"; } }
}
