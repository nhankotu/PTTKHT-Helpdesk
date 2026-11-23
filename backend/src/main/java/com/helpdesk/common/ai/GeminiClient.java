package com.helpdesk.common.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP Client gọi Google Gemini Developer API:
 * - generateContent (chế độ JSON cấu trúc + tùy chọn strict responseSchema, tách biệt system_instruction)
 * - embedContent (tạo vector embedding 768 chiều với taskType RETRIEVAL_DOCUMENT / RETRIEVAL_QUERY)
 */
@Component
public class GeminiClient {

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public GeminiClient(AiProperties aiProperties, ObjectMapper objectMapper) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
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

    public String getEmbeddingModelName() {
        return aiProperties.resolvedGeminiEmbeddingModel();
    }

    public int getEmbeddingDims() {
        return aiProperties.resolvedGeminiEmbeddingDims();
    }

    public JsonNode generateJson(String systemInstruction, String userPrompt) {
        return generateJson(systemInstruction, userPrompt, null);
    }

    public JsonNode generateJson(String systemInstruction, String userPrompt, Map<String, Object> responseSchema) {
        String apiKey = requireApiKey();

        String url = aiProperties.resolvedGeminiBaseUrl()
                + "/models/" + aiProperties.resolvedGeminiModel() + ":generateContent";

        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("temperature", 0.1);
        generationConfig.put("responseMimeType", "application/json");
        if (responseSchema != null && !responseSchema.isEmpty()) {
            generationConfig.put("responseSchema", responseSchema);
        }

        Map<String, Object> requestBody = Map.of(
                "system_instruction", Map.of(
                        "parts", List.of(Map.of("text", systemInstruction))
                ),
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", userPrompt))
                )),
                "generationConfig", generationConfig
        );

        String rawResponse = restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-goog-api-key", apiKey)
                .body(requestBody)
                .retrieve()
                .body(String.class);

        return extractJsonPayload(rawResponse);
    }

    public double[] embed(String text, String taskType, String title) {
        String apiKey = requireApiKey();
        String url = aiProperties.resolvedGeminiBaseUrl()
                + "/models/" + aiProperties.resolvedGeminiEmbeddingModel() + ":embedContent";

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("content", Map.of(
                "parts", List.of(Map.of("text", text != null ? text : ""))
        ));
        if (taskType != null && !taskType.isBlank()) {
            requestBody.put("taskType", taskType);
        }
        if ("RETRIEVAL_DOCUMENT".equals(taskType) && title != null && !title.isBlank()) {
            requestBody.put("title", title);
        }
        requestBody.put("outputDimensionality", aiProperties.resolvedGeminiEmbeddingDims());

        String rawResponse = restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-goog-api-key", apiKey)
                .body(requestBody)
                .retrieve()
                .body(String.class);

        return extractEmbeddingVector(rawResponse);
    }

    double[] extractEmbeddingVector(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new IllegalStateException("Gemini Embedding trả về phản hồi rỗng.");
        }
        try {
            JsonNode values = objectMapper.readTree(rawResponse).path("embedding").path("values");
            if (!values.isArray() || values.isEmpty()) {
                throw new IllegalStateException("Không tìm thấy mảng embedding.values trong phản hồi Gemini.");
            }
            double[] vec = new double[values.size()];
            for (int i = 0; i < values.size(); i++) {
                vec[i] = values.get(i).asDouble();
            }
            return vec;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Lỗi parse vector từ Gemini Embedding: " + ex.getMessage(), ex);
        }
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

    private String requireApiKey() {
        String apiKey = aiProperties.geminiApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY chưa được cấu hình.");
        }
        return apiKey.trim();
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
