package com.helpdesk.modules.knowledge.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpdesk.modules.knowledge.entity.KbChunk;
import com.helpdesk.modules.knowledge.rag.MarkdownChunker.ChunkPiece;
import com.helpdesk.modules.knowledge.rag.RagGenerator.ScoredChunk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HybridRagUpgradeTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Vietnamese accent folding: Câu hỏi không dấu khớp chính xác chunk KB có dấu")
    void accentlessVietnameseQueryMatchesAccentedKbChunk() {
        HybridKnowledgeRetriever retriever = new HybridKnowledgeRetriever(new NoOpEmbeddingClient(), objectMapper);

        KbChunk chunk = new KbChunk(1L, 0, "Thời gian kích hoạt",
                "Giao dịch chuyển khoản hợp lệ sẽ tự động kích hoạt sau 5 đến 15 phút.", 15, Instant.now());
        ReflectionTestUtils.setField(chunk, "id", 10L);

        List<ScoredChunk> results = retriever.retrieve("chuyen khoan chua kich hoat", List.of(chunk), 5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).chunk().getId()).isEqualTo(10L);
        assertThat(results.get(0).relevanceScore().doubleValue()).isGreaterThanOrEqualTo(0.5);
    }

    @Test
    @DisplayName("Hybrid Semantic Search: Tìm trúng chunk đồng nghĩa (0% trùng từ khóa) nhờ Cosine Similarity")
    void semanticSearchFindsSynonymousChunkWithoutKeywordOverlap() {
        EmbeddingClient mockEmbedding = mock(EmbeddingClient.class);
        when(mockEmbedding.embedQuery(anyString())).thenReturn(Optional.of(new double[]{0.9, 0.4, 0.1}));

        HybridKnowledgeRetriever retriever = new HybridKnowledgeRetriever(mockEmbedding, objectMapper);

        KbChunk relevantByMeaning = new KbChunk(1L, 0, "Chính sách thanh toán",
                "Giao dịch chuyển khoản sẽ tự động kích hoạt sau 5-15 phút.", 12, Instant.now());
        ReflectionTestUtils.setField(relevantByMeaning, "id", 20L);
        relevantByMeaning.setEmbeddingVector("[0.88, 0.45, 0.12]");

        KbChunk unrelated = new KbChunk(2L, 0, "Đổi mật khẩu",
                "Nhấn vào nút Quên mật khẩu để nhận mã OTP qua email.", 12, Instant.now());
        ReflectionTestUtils.setField(unrelated, "id", 21L);
        unrelated.setEmbeddingVector("[-0.5, 0.1, 0.9]");

        // Câu hỏi đồng nghĩa hoàn toàn không chứa từ khóa "chuyển khoản" hay "kích hoạt"
        List<ScoredChunk> results = retriever.retrieve(
                "tien bi tru roi ma goi pro van chua len",
                List.of(unrelated, relevantByMeaning),
                5
        );

        assertThat(results).hasSize(1);
        assertThat(results.get(0).chunk().getId()).isEqualTo(20L);
        assertThat(results.get(0).relevanceScore().doubleValue()).isGreaterThan(0.65);
    }

    @Test
    @DisplayName("MarkdownChunker: Tách theo heading và tạo sliding-window overlap cho đoạn dài > 250 từ")
    void markdownChunkerSplitsLongSectionWithOverlap() {
        MarkdownChunker chunker = new MarkdownChunker();
        String longParagraph = IntStream.rangeClosed(1, 320)
                .mapToObj(i -> "word" + i)
                .collect(Collectors.joining(" "));
        String markdown = "## 1. Quy trình hoàn tiền\n" + longParagraph + "\n\n## 2. Liên hệ\nGọi tổng đài 1900.";

        List<ChunkPiece> pieces = chunker.chunk("Chính sách hoàn tiền", markdown);

        assertThat(pieces).hasSize(3); // 2 chunks có overlap cho mục 1 + 1 chunk cho mục 2
        assertThat(pieces.get(0).heading()).isEqualTo("1. Quy trình hoàn tiền");
        assertThat(pieces.get(1).heading()).isEqualTo("1. Quy trình hoàn tiền");
        assertThat(pieces.get(1).content()).contains("word210"); // nằm trong vùng overlap 50 từ
        assertThat(pieces.get(2).heading()).isEqualTo("2. Liên hệ");
    }
}
