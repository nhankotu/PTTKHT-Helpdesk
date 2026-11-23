package com.helpdesk.modules.knowledge.rag;

import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * EmbeddingClient mặc định cho chế độ mock/offline/test (không gọi mạng ngoài).
 */
@Component
public class NoOpEmbeddingClient implements EmbeddingClient {

    @Override
    public Optional<double[]> embedDocument(String title, String content) {
        return Optional.empty();
    }

    @Override
    public Optional<double[]> embedQuery(String query) {
        return Optional.empty();
    }

    @Override
    public String embeddingRef() {
        return "lexical-only";
    }
}
