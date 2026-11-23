package com.helpdesk.modules.knowledge.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpdesk.modules.knowledge.entity.KbChunk;
import com.helpdesk.modules.knowledge.rag.RagGenerator.ScoredChunk;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Bộ truy xuất Hybrid RAG (Semantic Cosine Similarity + Vietnamese-normalized Lexical Search):
 * - Bỏ dấu tiếng Việt & lọc stop-words để khớp cả tiếng Việt có dấu / không dấu.
 * - Khi có vector embedding từ Gemini Embedding API -> kết hợp 72% Semantic + 28% Lexical.
 * - Khi không có vector (chế độ mock hoặc mất kết nối embedding) -> tự hạ cấp an toàn về Lexical.
 */
@Component
public class HybridKnowledgeRetriever {

    private static final double SEMANTIC_WEIGHT = 0.72;
    private static final double LEXICAL_WEIGHT = 0.28;
    private static final double MIN_HYBRID_SCORE = 0.45;
    private static final double MIN_SEMANTIC_ONLY_SCORE = 0.55;
    private static final double MIN_LEXICAL_ONLY_SCORE = 0.20;

    private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    private static final Set<String> STOP_WORDS = Set.copyOf(List.of(
            "toi", "ban", "anh", "chi", "em", "chung", "minh", "cua", "va", "la", "co", "khong",
            "duoc", "cho", "voi", "nhung", "thi", "ma", "de", "tai", "tu", "trong", "khi", "neu",
            "da", "dang", "se", "van", "bi", "cac", "mot", "nay", "do", "day", "nho", "giup",
            "vui", "long", "hoi", "ve", "lam", "sao", "the", "nao", "bao", "nhieu", "xin", "chao",
            "a", "an", "is", "are", "was", "were", "to", "of", "in", "on", "for", "and", "or",
            "my", "your", "me", "please", "help", "can", "how", "what", "why", "with", "that", "this"
    ));

    private final EmbeddingClient embeddingClient;
    private final ObjectMapper objectMapper;

    public HybridKnowledgeRetriever(EmbeddingClient embeddingClient, ObjectMapper objectMapper) {
        this.embeddingClient = embeddingClient;
        this.objectMapper = objectMapper;
    }

    public List<ScoredChunk> retrieve(String query, List<KbChunk> activePublishedChunks, int topK) {
        if (query == null || query.isBlank() || activePublishedChunks == null || activePublishedChunks.isEmpty()) {
            return List.of();
        }

        Set<String> queryTerms = tokenizeNormalized(query);
        Optional<double[]> queryVectorOpt = embeddingClient.embedQuery(query);
        double[] queryVec = queryVectorOpt.orElse(null);

        return activePublishedChunks.stream()
                .map(chunk -> scoreChunk(chunk, queryTerms, queryVec))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .sorted(Comparator.comparing(ScoredChunk::relevanceScore).reversed())
                .limit(Math.max(1, topK))
                .toList();
    }

    private Optional<ScoredChunk> scoreChunk(KbChunk chunk, Set<String> queryTerms, double[] queryVec) {
        double lexical = computeLexicalScore(queryTerms, chunk.getHeading(), chunk.getContent());
        double[] chunkVec = parseVector(chunk.getEmbeddingVector());

        if (queryVec != null && chunkVec != null && queryVec.length == chunkVec.length) {
            double semantic = Math.max(0.0, cosineSimilarity(queryVec, chunkVec));
            double hybrid = SEMANTIC_WEIGHT * semantic + LEXICAL_WEIGHT * lexical;
            if (hybrid >= MIN_HYBRID_SCORE || semantic >= MIN_SEMANTIC_ONLY_SCORE) {
                double clamped = Math.min(1.0, Math.max(0.0, hybrid));
                return Optional.of(new ScoredChunk(
                        chunk, BigDecimal.valueOf(clamped).setScale(4, RoundingMode.HALF_UP)
                ));
            }
            return Optional.empty();
        }

        // Chế độ lexical-only (mock hoặc chunk chưa được embed)
        if (lexical >= MIN_LEXICAL_ONLY_SCORE) {
            double clamped = Math.min(1.0, lexical);
            return Optional.of(new ScoredChunk(
                    chunk, BigDecimal.valueOf(clamped).setScale(4, RoundingMode.HALF_UP)
            ));
        }
        return Optional.empty();
    }

    public double computeLexicalScore(Set<String> queryTerms, String heading, String content) {
        if (queryTerms.isEmpty()) {
            return 0.0;
        }
        Set<String> headingTerms = tokenizeNormalized(heading);
        Set<String> bodyTerms = tokenizeNormalized(content);
        if (headingTerms.isEmpty() && bodyTerms.isEmpty()) {
            return 0.0;
        }

        double weightedHits = 0.0;
        for (String term : queryTerms) {
            if (headingTerms.contains(term)) {
                weightedHits += 1.25; // ưu tiên khớp tiêu đề mục
            } else if (bodyTerms.contains(term)) {
                weightedHits += 1.0;
            }
        }
        return Math.min(1.0, weightedHits / (double) queryTerms.size());
    }

    public Set<String> tokenizeNormalized(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        String folded = foldVietnamese(text);
        return Arrays.stream(folded.split("[^a-z0-9]+"))
                .filter(w -> w.length() >= 2 && !STOP_WORDS.contains(w))
                .collect(Collectors.toSet());
    }

    public String foldVietnamese(String input) {
        String lower = input.toLowerCase(Locale.ROOT)
                .replace('đ', 'd')
                .replace('Đ', 'd');
        String normalized = Normalizer.normalize(lower, Normalizer.Form.NFD);
        return DIACRITICS.matcher(normalized).replaceAll("");
    }

    public double cosineSimilarity(double[] a, double[] b) {
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private double[] parseVector(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, double[].class);
        } catch (Exception ex) {
            return null;
        }
    }
}
