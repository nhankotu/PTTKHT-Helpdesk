package com.helpdesk.modules.knowledge.rag;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class GroundedRagGenerator implements RagGenerator {

    @Override
    public RagDraftResult generate(String query, List<ScoredChunk> retrievedChunks) {
        if (retrievedChunks == null || retrievedChunks.isEmpty()) {
            return new RagDraftResult(false, null, List.of(), BigDecimal.ZERO, "grounded-rag-v1", "rag-v1.0");
        }
        List<ScoredChunk> top = retrievedChunks.stream().limit(3).toList();
        String draft = "Tham khảo từ Knowledge Base đã xuất bản:\n" + top.stream()
                .map(sc -> "- " + sc.chunk().getContent())
                .collect(Collectors.joining("\n"));
        List<Long> citedIds = top.stream().map(sc -> sc.chunk().getId()).toList();
        return new RagDraftResult(true, draft, citedIds, top.get(0).relevanceScore(), "grounded-rag-v1", "rag-v1.0");
    }
}
