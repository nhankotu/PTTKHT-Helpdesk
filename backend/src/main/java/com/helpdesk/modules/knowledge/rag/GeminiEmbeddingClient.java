package com.helpdesk.modules.knowledge.rag;

import com.helpdesk.common.ai.GeminiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Triển khai EmbeddingClient dùng Google Gemini Embedding API (gemini-embedding-001, 768 dims).
 * Chỉ kích hoạt khi app.ai.provider=gemini. Nếu lỗi mạng/rate limit -> log cảnh báo và trả empty
 * để HybridKnowledgeRetriever tự hạ cấp an toàn về lexical search.
 */
@Component
@Primary
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "gemini")
public class GeminiEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiEmbeddingClient.class);

    private final GeminiClient geminiClient;

    public GeminiEmbeddingClient(GeminiClient geminiClient) {
        this.geminiClient = geminiClient;
    }

    @Override
    public Optional<double[]> embedDocument(String title, String content) {
        try {
            return Optional.of(geminiClient.embed(content, "RETRIEVAL_DOCUMENT", title));
        } catch (Exception ex) {
            log.warn("Gemini embedDocument failed (degrading to lexical): {}", ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<double[]> embedQuery(String query) {
        try {
            return Optional.of(geminiClient.embed(query, "RETRIEVAL_QUERY", null));
        } catch (Exception ex) {
            log.warn("Gemini embedQuery failed (degrading to lexical): {}", ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public String embeddingRef() {
        return geminiClient.getEmbeddingModelName() + ":" + geminiClient.getEmbeddingDims();
    }
}
