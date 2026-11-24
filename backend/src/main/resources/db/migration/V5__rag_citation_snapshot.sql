ALTER TABLE ai_citation
  ADD COLUMN quoted_text TEXT NULL,
  ADD COLUMN intent_ids JSON NULL;
ALTER TABLE rag_request ADD UNIQUE KEY uq_rag_request_ai_run (ai_run_id);
