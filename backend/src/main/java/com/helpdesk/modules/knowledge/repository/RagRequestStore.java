package com.helpdesk.modules.knowledge.repository;

import com.helpdesk.common.exception.BusinessException;
import com.helpdesk.common.exception.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Independent committed claims survive rollback, client replay and ambiguous provider failures. */
@Repository
public class RagRequestStore {
    private final JdbcTemplate jdbc;
    public RagRequestStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record Snapshot(boolean fresh, Long runId) {}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Snapshot begin(long actor, String key, String hash) {
        boolean fresh = jdbc.update("INSERT IGNORE INTO rag_request(actor_id,request_key,input_hash) VALUES (?,?,?)", actor, key, hash) == 1;
        return jdbc.queryForObject("SELECT input_hash, ai_run_id FROM rag_request WHERE actor_id=? AND request_key=?", (rs, row) -> {
            if (!hash.equals(rs.getString(1))) throw BusinessException.conflict(ErrorCode.IDEMPOTENCY_KEY_PAYLOAD_MISMATCH, "Idempotency-Key đã dùng cho đầu vào khác.");
            return new Snapshot(fresh, rs.getObject(2, Long.class));
        }, actor, key);
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(long actor, String key, int stage) {
        if (stage != 1 && stage != 2) throw new IllegalArgumentException("stage");
        String column = stage == 1 ? "stage1_claimed" : "stage2_claimed";
        return jdbc.update("UPDATE rag_request SET " + column + "=TRUE WHERE actor_id=? AND request_key=? AND " + column + "=FALSE AND ai_run_id IS NULL"
                + (stage == 2 ? " AND stage1_claimed=TRUE" : ""), actor, key) == 1;
    }
    /** Joins the caller's transaction: run/citations/cache pointer are committed together. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(long actor, String key, long run, String model, String reason, int latency, String stages, String evidenceFingerprint) {
        if (jdbc.update("UPDATE rag_request SET ai_run_id=?,model_name=?,reason_code=?,latency_ms=?,stage_metadata=?,evidence_fingerprint=?,completed_at=CURRENT_TIMESTAMP(6) WHERE actor_id=? AND request_key=? AND ai_run_id IS NULL",
                run, model, reason, latency, stages, evidenceFingerprint, actor, key) != 1) throw new IllegalStateException("RAG request already completed");
    }
    public String evidenceFingerprint(long actor, String key) { return jdbc.queryForObject("SELECT evidence_fingerprint FROM rag_request WHERE actor_id=? AND request_key=?", String.class, actor, key); }
    public record Sources(String fingerprint, java.util.List<Long> ids) {}
    public Sources sources(long runId) {
        var rows=jdbc.query("SELECT evidence_fingerprint,stage_metadata FROM rag_request WHERE ai_run_id=?", (rs,row)-> {
            java.util.List<Long> ids=new java.util.ArrayList<>();
            try {
                String metadata=rs.getString(2);
                if(metadata!=null) for(var item:new com.fasterxml.jackson.databind.ObjectMapper().readTree(metadata).path("context")) ids.add(item.path("chunkId").longValue());
            } catch(Exception ex) { return new Sources(null,java.util.List.of()); }
            return new Sources(rs.getString(1),java.util.List.copyOf(ids));
        },runId);
        return rows.isEmpty()?null:rows.get(0);
    }
}
