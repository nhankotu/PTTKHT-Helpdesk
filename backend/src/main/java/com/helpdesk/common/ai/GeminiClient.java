package com.helpdesk.common.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * HTTP Client gọi Google Gemini Developer API (generateContent) với chế độ JSON cấu trúc.
 * Tách biệt rõ system_instruction và user contents để chống Prompt Injection.
 */
@Component
public class GeminiClient {

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final com.helpdesk.common.ai.structured.BoundedJsonHttpTransport boundedTransport;

    public GeminiClient(AiProperties aiProperties, ObjectMapper objectMapper) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.boundedTransport = new com.helpdesk.common.ai.structured.BoundedJsonHttpTransport(objectMapper);
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(aiProperties.resolvedTimeoutMs()));
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    public String getModelName() {
        return aiProperties.resolvedGeminiModel();
    }

    /** One HTTP attempt, bounded body/output/time; no JSON repair or transport retry. */
    public JsonNode generateBoundedJson(String system, String data, String model, int timeoutMs, int outputTokens) {
        return generateBoundedReply(system, data, model, timeoutMs, outputTokens).json();
    }

    public com.helpdesk.common.ai.structured.StructuredModelAdapter.Reply generateBoundedReply(String system, String data, String model, int timeoutMs, int outputTokens) {
        if (aiProperties.geminiApiKey() == null || aiProperties.geminiApiKey().isBlank()) throw new IllegalStateException("AI key missing");
        if (!model.matches("[A-Za-z0-9._-]{1,100}") || timeoutMs < 100) throw new IllegalArgumentException("Invalid model budget");
        Map<String, Object> body = Map.of("system_instruction", Map.of("parts", List.of(Map.of("text", system))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", data)))),
                "generationConfig", Map.of("temperature", 0.0, "responseMimeType", "application/json", "maxOutputTokens", outputTokens));
        JsonNode outer = boundedTransport.post(aiProperties.resolvedGeminiBaseUrl() + "/models/" + model + ":generateContent", "X-goog-api-key", aiProperties.geminiApiKey().trim(), body, timeoutMs);
                    JsonNode candidate = outer.path("candidates").path(0);
                    if (!"STOP".equals(candidate.path("finishReason").asText())) throw new IllegalStateException("AI incomplete output");
                    JsonNode parts = candidate.path("content").path("parts");
                    if (!parts.isArray() || parts.size() != 1 || !parts.get(0).path("text").isTextual()) throw new IllegalStateException("AI invalid output");
                    String raw = parts.get(0).path("text").textValue();
        JsonNode result = boundedTransport.parse(raw);
        return new com.helpdesk.common.ai.structured.StructuredModelAdapter.Reply(result,
                usage(outer.path("usageMetadata").path("promptTokenCount")), usage(outer.path("usageMetadata").path("candidatesTokenCount")));
    }

    private Long usage(JsonNode value) { return value.isIntegralNumber() && value.canConvertToLong() && value.longValue() >= 0 ? value.longValue() : null; }

    public JsonNode generateJson(String systemInstruction, String userPrompt) {
        String apiKey = aiProperties.geminiApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY chưa được cấu hình.");
        }

        String url = aiProperties.resolvedGeminiBaseUrl()
                + "/models/" + aiProperties.resolvedGeminiModel() + ":generateContent";

        Map<String, Object> requestBody = Map.of(
                "system_instruction", Map.of(
                        "parts", List.of(Map.of("text", systemInstruction))
                ),
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", userPrompt))
                )),
                "generationConfig", Map.of(
                        "temperature", 0.1,
                        "responseMimeType", "application/json"
                )
        );

        String rawResponse = restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-goog-api-key", apiKey.trim())
                .body(requestBody)
                .retrieve()
                .body(String.class);

        return extractJsonPayload(rawResponse);
    }

    JsonNode extractJsonPayload(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new IllegalStateException("Gemini trả về phản hồi rỗng.");
        }
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode textNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
            if (textNode.isMissingNode() || textNode.asText().isBlank()) {
                throw new IllegalStateException("Không tìm thấy nội dung text trong phản hồi Gemini.");
            }
            String text = stripMarkdownFences(textNode.asText().trim());
            return objectMapper.readTree(text);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể parse JSON từ phản hồi Gemini: " + ex.getMessage(), ex);
        }
    }

    private String stripMarkdownFences(String text) {
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            if (firstNewline > 0) {
                text = text.substring(firstNewline + 1);
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }
        }
        return text.trim();
    }
}
