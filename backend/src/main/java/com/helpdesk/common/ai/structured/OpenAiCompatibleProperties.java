package com.helpdesk.common.ai.structured;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai.openai-compatible")
public record OpenAiCompatibleProperties(String baseUrl, String apiKey, Boolean allowNoKey, String tokenLimitField) {
    public String resolvedBaseUrl() { return (baseUrl == null || baseUrl.isBlank() ? "https://api.openai.com/v1" : baseUrl).replaceAll("/+$", ""); }
    public String resolvedTokenLimitField() {
        String value = tokenLimitField == null || tokenLimitField.isBlank() ? "max_completion_tokens" : tokenLimitField;
        if (!java.util.Set.of("max_completion_tokens", "max_tokens").contains(value)) throw new IllegalArgumentException("Invalid token limit field");
        return value;
    }
}
