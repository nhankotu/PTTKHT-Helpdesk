ALTER TABLE ai_run MODIFY COLUMN model_name VARCHAR(260) NOT NULL;
ALTER TABLE rag_request
  MODIFY COLUMN model_name VARCHAR(260) NULL,
  ADD COLUMN stage_metadata JSON NULL,
  ADD COLUMN evidence_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL;
