package com.helpdesk.common.ai.structured;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class OpenAiCompatibleAdapter implements StructuredModelAdapter {
    private final OpenAiCompatibleProperties properties;
    private final BoundedJsonHttpTransport transport;
    public OpenAiCompatibleAdapter(OpenAiCompatibleProperties properties, BoundedJsonHttpTransport transport) { this.properties = properties; this.transport = transport; }
    public String provider() { return "openai-compatible"; }
    public Reply generate(Request request) {
        boolean noKey = properties.apiKey() == null || properties.apiKey().isBlank();
        // Anonymous access is allowed only for an explicitly configured local endpoint.
        String host = java.net.URI.create(properties.resolvedBaseUrl()).getHost();
        if (noKey && !(Boolean.TRUE.equals(properties.allowNoKey()) && Set.of("localhost", "127.0.0.1", "::1", "[::1]").contains(host))) throw new IllegalStateException("Model key missing");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", request.model());
        payload.put("messages", List.of(Map.of("role", "system", "content", request.system()), Map.of("role", "user", "content", request.inputJson())));
        payload.put("response_format", Map.of("type", "json_object"));
        payload.put(properties.resolvedTokenLimitField(), request.maxOutputTokens());
        payload.put("stream", false);
        JsonNode outer = transport.post(properties.resolvedBaseUrl() + "/chat/completions", "Authorization", noKey ? null : "Bearer " + properties.apiKey().trim(), payload, request.timeoutMs());
        JsonNode choices = outer.path("choices"), choice = choices.path(0), message = choice.path("message");
        if (!choices.isArray() || choices.size() != 1 || !"stop".equals(choice.path("finish_reason").asText())
                || message.hasNonNull("refusal") || message.hasNonNull("tool_calls") || !message.path("content").isTextual()) throw new IllegalStateException("Incomplete or unsupported model response");
        return new Reply(transport.parse(message.path("content").textValue()), tokens(outer.path("usage").path("prompt_tokens")), tokens(outer.path("usage").path("completion_tokens")));
    }
    private Long tokens(JsonNode value) { return value.isIntegralNumber() && value.canConvertToLong() && value.longValue() >= 0 ? value.longValue() : null; }
}
