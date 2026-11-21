package com.helpdesk.common.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpdesk.modules.knowledge.entity.KbChunk;
import com.helpdesk.modules.knowledge.rag.GeminiRagGenerator;
import com.helpdesk.modules.knowledge.rag.RagGenerator.RagDraftResult;
import com.helpdesk.modules.knowledge.rag.RagGenerator.ScoredChunk;
import com.helpdesk.modules.routing.classifier.GeminiTicketClassifier;
import com.helpdesk.modules.routing.classifier.TicketClassifier.ClassificationSuggestion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeminiAiAdaptersTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private GeminiClient geminiClient;

    @Test
    @DisplayName("UC04 GeminiTicketClassifier: Parse JSON hợp lệ từ Gemini và chuẩn hóa confidence 4 chữ số")
    void classifierParsesValidGeminiJson() throws Exception {
        when(geminiClient.getModelName()).thenReturn("gemini-2.5-flash");
        when(geminiClient.generateJson(anyString(), anyString())).thenReturn(objectMapper.readTree("""
                {"categoryCode": "payment", "confidence": 0.96, "reason": "Khách hỏi về giao dịch chuyển khoản"}
                """));

        GeminiTicketClassifier classifier = new GeminiTicketClassifier(geminiClient);
        ClassificationSuggestion suggestion = classifier.classify(
                "Chuyển khoản chưa kích hoạt",
                "Tôi đã thanh toán 500k",
                List.of("PAYMENT", "ACCOUNT", "TECHNICAL", "ORDER", "OTHER")
        );

        assertThat(suggestion.categoryCode()).isEqualTo("PAYMENT");
        assertThat(suggestion.confidence()).isEqualByComparingTo("0.9600");
        assertThat(suggestion.modelVersion()).isEqualTo("gemini-2.5-flash");
    }

    @Test
    @DisplayName("UC04 GeminiTicketClassifier: Ném lỗi khi Gemini trả về categoryCode ngoài danh sách hợp lệ để RoutingService fallback")
    void classifierRejectsUnknownCategoryCode() throws Exception {
        when(geminiClient.generateJson(anyString(), anyString())).thenReturn(objectMapper.readTree("""
                {"categoryCode": "HACKED_CAT", "confidence": 0.99, "reason": "invalid"}
                """));

        GeminiTicketClassifier classifier = new GeminiTicketClassifier(geminiClient);

        assertThatThrownBy(() -> classifier.classify("Test", "Body", List.of("PAYMENT", "OTHER")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HACKED_CAT");
    }

    @Test
    @DisplayName("UC07 GeminiRagGenerator: Parse JSON gợi ý RAG kèm citedChunkIds hợp lệ")
    void ragGeneratorParsesGroundedAnswerWithCitations() throws Exception {
        when(geminiClient.getModelName()).thenReturn("gemini-2.5-flash");
        when(geminiClient.generateJson(anyString(), anyString())).thenReturn(objectMapper.readTree("""
                {
                  "canAnswer": true,
                  "answerText": "Giao dịch chuyển khoản sẽ tự động kích hoạt sau 5-15 phút.",
                  "citedChunkIds": [501],
                  "confidence": 0.92
                }
                """));

        KbChunk chunk = new KbChunk(70L, 0, "Thanh toán", "Tự động kích hoạt sau 5-15 phút.", 10, Instant.now());
        ReflectionTestUtils.setField(chunk, "id", 501L);

        GeminiRagGenerator generator = new GeminiRagGenerator(geminiClient);
        RagDraftResult result = generator.generate(
                "Chuyển khoản bao lâu thì kích hoạt?",
                List.of(new ScoredChunk(chunk, new BigDecimal("0.8500")))
        );

        assertThat(result.canAnswer()).isTrue();
        assertThat(result.answerText()).contains("5-15 phút");
        assertThat(result.citedChunkIds()).containsExactly(501L);
        assertThat(result.confidence()).isEqualByComparingTo("0.9200");
        assertThat(result.modelName()).isEqualTo("gemini-2.5-flash");
    }

    @Test
    @DisplayName("GeminiClient.extractJsonPayload: Tự động gỡ markdown code fence ```json ... ```")
    void geminiClientStripsMarkdownFences() {
        GeminiClient realClient = new GeminiClient(
                new AiProperties("gemini", "dummy", "gemini-2.5-flash", null, 5000),
                objectMapper
        );
        String raw = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          { "text": "```json\\n{\\"categoryCode\\":\\"PAYMENT\\",\\"confidence\\":0.95}\\n```" }
                        ]
                      }
                    }
                  ]
                }
                """;
        var node = realClient.extractJsonPayload(raw);
        assertThat(node.path("categoryCode").asText()).isEqualTo("PAYMENT");
        assertThat(node.path("confidence").asDouble()).isEqualTo(0.95);
    }
}
