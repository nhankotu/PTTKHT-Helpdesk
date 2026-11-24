package com.helpdesk.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.helpdesk.modules.identity.dto.*;
import com.helpdesk.modules.intake.dto.IntakeDtos.*;
import com.helpdesk.modules.knowledge.repository.RagRequestStore;
import com.helpdesk.modules.knowledge.dto.KnowledgeDtos.*;
import com.helpdesk.modules.knowledge.entity.KbAccessScope;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class TwoStageRagIntegrationTest extends AbstractMySqlIntegrationTest {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired RagRequestStore store;
    @Autowired PlatformTransactionManager transactions;

    private HttpHeaders agent() {
        return login("agent.an@helpdesk.local");
    }
    private HttpHeaders login(String email) {
        var auth = http.postForEntity("/api/v1/auth/login", new LoginRequest(email, "Password123!"), AuthResponse.class);
        assertThat(auth.getStatusCode()).isEqualTo(HttpStatus.OK);
        HttpHeaders headers = new HttpHeaders(); headers.setBearerAuth(auth.getBody().accessToken()); return headers;
    }
    private long ticket() {
        HttpHeaders headers = new HttpHeaders(); headers.set("Idempotency-Key", UUID.randomUUID().toString());
        var response = http.postForEntity("/api/v1/intake/web-requests", new HttpEntity<>(new WebIntakeRequest("Hỗ trợ giao hàng", "Tôi cần hỏi chính sách", null,
                new ContactInfo("Khách test", UUID.randomUUID()+"@example.com", "0912345678"), List.of()), headers), WebIntakeResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED); return response.getBody().ticketId();
    }
    @Test void clarificationIsPersistedWithoutCitationAndReplayDoesNotCallAgain() {
        long ticket = ticket(); HttpHeaders headers = agent(); String key = UUID.randomUUID().toString(); headers.set("Idempotency-Key", key);
        String uri = "/api/v1/tickets/" + ticket + "/ai/suggestions";
        var body = Map.of("customQuery", "Shop tính ship bao nhiêu vậy?", "topK", 10);
        var first = http.postForEntity(uri, new HttpEntity<>(body, headers), JsonNode.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(first.getBody().path("outcome").asText()).isEqualTo("CLARIFICATION_REQUIRED");
        assertThat(first.getBody().path("citations").size()).isZero();
        assertThat(first.getBody().path("answerText").asText()).contains("Giá trị đơn hàng", "thành viên");
        var replay = http.postForEntity(uri, new HttpEntity<>(body, headers), JsonNode.class);
        assertThat(replay.getBody().path("id")).isEqualTo(first.getBody().path("id"));
        assertThat(jdbc.queryForObject("SELECT stage2_claimed FROM rag_request WHERE request_key=?", Boolean.class, key)).isFalse();
        String audit = jdbc.queryForObject("SELECT stage_metadata FROM rag_request WHERE request_key=?", String.class, key);
        assertThat(audit).contains("queryPlan", "sources", "sha256", "model1", "mock-query-v1").doesNotContain("test-key");
        var changed = http.postForEntity(uri, new HttpEntity<>(Map.of("customQuery", "Câu hỏi khác"), headers), JsonNode.class);
        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
    @Test void claimIsAtomicAndSurvivesCallerRollback() throws Exception {
        String key = UUID.randomUUID().toString(), hash = "a".repeat(64);
        assertThat(store.begin(9999, key, hash).fresh()).isTrue();
        assertThatThrownBy(() -> new TransactionTemplate(transactions).execute(status -> {
            assertThat(store.claim(9999, key, 1)).isTrue(); throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(store.claim(9999, key, 1)).isFalse();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var a = executor.submit(() -> store.claim(9999, key, 2));
            var b = executor.submit(() -> store.claim(9999, key, 2));
            assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        } finally { executor.shutdownNow(); }
        assertThat(store.begin(9999, key, hash).fresh()).isFalse();
    }
    @Test void agentOutsideTicketScopeCannotReadHistoryOrInvokeModels() {
        long ticket = ticket();
        String teamCode = "RAG_" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO support_team(code,name,supervisor_id) SELECT ?,?,id FROM app_user WHERE email='supervisor@helpdesk.local'", teamCode, "Other " + teamCode);
        Long otherTeam = jdbc.queryForObject("SELECT id FROM support_team WHERE code=?", Long.class, teamCode);
        jdbc.update("UPDATE ticket SET assigned_agent_id=NULL, team_id=? WHERE id=?", otherTeam, ticket);
        HttpHeaders headers = agent();
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        var result = http.postForEntity("/api/v1/tickets/" + ticket + "/ai/suggestions", new HttpEntity<>(Map.of("customQuery", "Chính sách"), headers), JsonNode.class);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var history = http.exchange("/api/v1/tickets/" + ticket + "/ai/suggestions", HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
        assertThat(history.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
    @Test void cachedDraftCannotSurviveChangedChunkContent() {
        long ticket = ticket(); HttpHeaders supervisor = login("supervisor@helpdesk.local");
        var created = http.postForEntity("/api/v1/kb/articles", new HttpEntity<>(new KbArticleCreateRequest(
                "KB-AD-" + UUID.randomUUID().toString().substring(0,8), null, KbAccessScope.PUBLIC, false,
                "Adapterregression đóng gói", "Quy tắc hộp", "Adapterregression: luôn giữ hộp khi gửi lại sản phẩm."), supervisor), KbArticleDetailDto.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long version = created.getBody().versions().get(0).id();
        http.postForEntity("/api/v1/kb/versions/"+version+"/submit", new HttpEntity<>(supervisor), KbVersionDto.class);
        http.postForEntity("/api/v1/kb/versions/"+version+"/publish", new HttpEntity<>(supervisor), KbVersionDto.class);
        HttpHeaders headers = agent(); headers.set("Idempotency-Key", UUID.randomUUID().toString());
        String uri = "/api/v1/tickets/"+ticket+"/ai/suggestions";
        var body = new HttpEntity<>(Map.of("customQuery", "Adapterregression đóng gói giữ hộp", "topK", 5), headers);
        var response = http.postForEntity(uri, body, JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().path("outcome").asText()).isEqualTo("SUGGESTED");
        long chunk = response.getBody().path("citations").get(0).path("kbChunkId").asLong();
        jdbc.update("UPDATE kb_chunk SET content=CONCAT(content,' updated') WHERE id=?", chunk);
        assertThat(http.postForEntity(uri, body, JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        var history = http.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
        JsonNode entry = history.getBody().get(0);
        assertThat(entry.path("answerText").isNull()).isTrue(); assertThat(entry.path("citations").size()).isZero();
        var feedback = http.postForEntity("/api/v1/ai/runs/"+response.getBody().path("id").asLong()+"/feedback", new HttpEntity<>(Map.of("decision","ACCEPTED_AS_IS"), headers), JsonNode.class);
        assertThat(feedback.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
    @Test void openApiIncludesClarificationAndIdempotencyHeader() throws Exception {
        var response = http.getForEntity("/v3/api-docs", JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().toString()).contains("CLARIFICATION_REQUIRED", "Idempotency-Key");
        if (Boolean.getBoolean("helpdesk.openapi.write")) {
            var root = (com.fasterxml.jackson.databind.node.ObjectNode) response.getBody();
            root.set("servers", new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(List.of(Map.of("url", "http://localhost:8080", "description", "Local development"))));
            java.nio.file.Files.writeString(java.nio.file.Path.of("docs/openapi.json"), new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(root)+"\n", java.nio.charset.StandardCharsets.UTF_8);
        }
    }
    @Test void keylessRequestsAreRejectedBeforeAnyStage() {
        long ticket = ticket();
        var result = http.postForEntity("/api/v1/tickets/"+ticket+"/ai/suggestions",new HttpEntity<>(Map.of("customQuery","Chính sách"),agent()),JsonNode.class);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
    @Test void excessiveHistoryReturnsPersistedFallbackInsteadOfTextColumnOverflow() {
        long ticket = ticket();
        for (int i=0;i<2;i++) jdbc.update("INSERT INTO ticket_message(conversation_id,ticket_id,sender_type,direction,visibility,body) SELECT conversation_id,id,'CUSTOMER','INBOUND','PUBLIC',? FROM ticket WHERE id=?", "a".repeat(40000), ticket);
        HttpHeaders headers=agent(); headers.set("Idempotency-Key",UUID.randomUUID().toString());
        var response=http.postForEntity("/api/v1/tickets/"+ticket+"/ai/suggestions",new HttpEntity<>(Map.of(),headers),JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().path("fallbackReason").asText()).isEqualTo("INPUT_LIMIT_EXCEEDED");
    }
}
