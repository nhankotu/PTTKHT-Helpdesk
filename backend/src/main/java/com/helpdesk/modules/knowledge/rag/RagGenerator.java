package com.helpdesk.modules.knowledge.rag;

import com.helpdesk.modules.knowledge.entity.KbChunk;

import java.math.BigDecimal;
import java.util.List;

public interface RagGenerator {

    record ScoredChunk(KbChunk chunk, BigDecimal relevanceScore) {
    }

    record RagDraftResult(
            boolean canAnswer,
            String answerText,
            List<Long> citedChunkIds,
            BigDecimal confidence,
            String modelName,
            String promptVersion
    ) {
    }

    RagDraftResult generate(String query, List<ScoredChunk> retrievedChunks);
}
