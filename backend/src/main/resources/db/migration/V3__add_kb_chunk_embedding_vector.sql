-- ============================================================================
-- V3: BỔ SUNG CỘT VECTOR EMBEDDING CHO HYBRID SEMANTIC RAG (UC06 & UC07)
-- ============================================================================

ALTER TABLE kb_chunk
    ADD COLUMN embedding_vector JSON NULL COMMENT 'Vector embedding (float[]) sinh bởi Gemini Embedding API' AFTER embedding_ref;
