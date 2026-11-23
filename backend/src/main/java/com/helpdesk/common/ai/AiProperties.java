package com.helpdesk.common.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        String provider,
        String geminiApiKey,
        String geminiModel,
        String geminiEmbeddingModel,
        Integer geminiEmbeddingDims,
        String geminiBaseUrl,
        Integer timeoutMs
) {
    public String resolvedProvider() {
        return (provider == null || provider.isBlank()) ? "mock" : provider.trim().toLowerCase();
    }

    public String resolvedGeminiModel() {
        return (geminiModel == null || geminiModel.isBlank()) ? "gemini-2.5-flash" : geminiModel.trim();
    }

    public String resolvedGeminiEmbeddingModel() {
        return (geminiEmbeddingModel == null || geminiEmbeddingModel.isBlank())
                ? "gemini-embedding-001"
                : geminiEmbeddingModel.trim();
    }

    public int resolvedGeminiEmbeddingDims() {
        return (geminiEmbeddingDims == null || geminiEmbeddingDims < 64) ? 768 : geminiEmbeddingDims;
    }

    public String resolvedGeminiBaseUrl() {
        String base = (geminiBaseUrl == null || geminiBaseUrl.isBlank())
                ? "https://generativelanguage.googleapis.com/v1beta"
                : geminiBaseUrl.trim();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    public int resolvedTimeoutMs() {
        return (timeoutMs == null || timeoutMs < 1000) ? 15000 : timeoutMs;
    }
}
