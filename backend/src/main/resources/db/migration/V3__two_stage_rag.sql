ALTER TABLE ai_run DROP CHECK chk_ai_run_outcome_fields;
ALTER TABLE ai_run DROP CHECK chk_ai_run_outcome;
ALTER TABLE ai_run DROP CHECK chk_ai_run_fallback;
ALTER TABLE ai_run ADD CONSTRAINT chk_ai_run_outcome CHECK
  (outcome IN ('SUGGESTED','AUTO_SENT','FALLBACK_TO_AGENT','FAILED','CLARIFICATION_REQUIRED'));
ALTER TABLE ai_run ADD CONSTRAINT chk_ai_run_fallback CHECK
  (fallback_reason IS NULL OR fallback_reason IN (
    'NO_RELEVANT_KB','CONFLICTING_SOURCES','OUTDATED_OR_REVOKED_KB','LOW_CONFIDENCE',
    'OUT_OF_AUTO_SCOPE','CUSTOMER_REQUESTED_HUMAN','CITATION_INVALID','AI_SERVICE_ERROR',
    'QUERY_NEEDS_CLARIFICATION','QUERY_PLAN_INVALID','RAG_OUTPUT_INVALID','INPUT_LIMIT_EXCEEDED',
    'RULE_SOURCE_MISMATCH','CONTEXT_LIMIT_EXCEEDED','REQUEST_DEADLINE_EXCEEDED'));
ALTER TABLE ai_run ADD CONSTRAINT chk_ai_run_outcome_fields CHECK (
  (outcome IN ('SUGGESTED','AUTO_SENT') AND answer_text IS NOT NULL)
  OR (outcome = 'CLARIFICATION_REQUIRED' AND answer_text IS NOT NULL AND fallback_reason = 'QUERY_NEEDS_CLARIFICATION')
  OR (outcome IN ('FALLBACK_TO_AGENT','FAILED') AND fallback_reason IS NOT NULL AND answer_text IS NULL));

CREATE TABLE rag_request (
  actor_id BIGINT NOT NULL,
  request_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  input_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  stage1_claimed BOOLEAN NOT NULL DEFAULT FALSE,
  stage2_claimed BOOLEAN NOT NULL DEFAULT FALSE,
  ai_run_id BIGINT NULL,
  model_name VARCHAR(200) NULL,
  reason_code VARCHAR(50) NULL,
  latency_ms INT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  completed_at DATETIME(6) NULL,
  PRIMARY KEY (actor_id, request_key),
  CONSTRAINT fk_rag_request_run FOREIGN KEY (ai_run_id) REFERENCES ai_run(id),
  CONSTRAINT chk_rag_stage_order CHECK (stage2_claimed = FALSE OR stage1_claimed = TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
