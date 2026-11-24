package com.helpdesk.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpdesk.modules.knowledge.entity.KbChunk;
import com.helpdesk.modules.knowledge.entity.KbVersion;
import com.helpdesk.modules.knowledge.entity.KbVersionStatus;
import com.helpdesk.modules.knowledge.rag.RagGenerator;
import com.helpdesk.modules.knowledge.rag.RagGenerator.ScoredChunk;
import com.helpdesk.modules.knowledge.repository.KbChunkRepository;
import com.helpdesk.modules.knowledge.service.AiHelpdeskFacade;
import com.helpdesk.modules.knowledge.service.KnowledgeBaseService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Offline benchmark: actual production Java methods, frozen reviewed KB, no Spring/network/database. */
class CellphonesRetrievalBaselineTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int TOP_K = Integer.getInteger("helpdesk.retrieval.topK", 5);

    @Test
    void evaluateFrozenQuestionsWithProductionRetrievalMethods() throws Exception {
        Path root = repositoryRoot();
        Path data = root.resolve("data/cellphones");
        Path evaluation = data.resolve("evaluation");
        JsonNode lock = read(evaluation.resolve("source_lock.json"));
        JsonNode review = read(data.resolve("review.json"));
        JsonNode manifest = read(data.resolve("sources.json"));
        List<JsonNode> articles = jsonLines(data.resolve("kb/articles.jsonl"));
        List<JsonNode> cases = jsonLines(evaluation.resolve("test_cases.jsonl"));
        assertThat(cases).hasSize(50);

        Clock clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
        KbChunkRepository repository = mock(KbChunkRepository.class);
        RagGenerator generator = mock(RagGenerator.class);
        List<KbChunk> captured = new ArrayList<>();
        doAnswer(invocation -> {
            Iterable<KbChunk> batch = invocation.getArgument(0);
            batch.forEach(captured::add);
            return batch;
        }).when(repository).saveAll(any());
        KnowledgeBaseService kb = new KnowledgeBaseService(null, null, repository, null, clock);
        Map<Long, String> keys = new HashMap<>();
        long fixtureId = 1;
        long versionId = 1;
        for (JsonNode article : articles) {
            String code = article.path("articleCode").asText();
            String content = article.path("content").asText();
            assertThat(hash(content.getBytes(StandardCharsets.UTF_8))).isEqualTo(lock.path("documents").path(code).path("content_sha256").asText());
            assertThat(review.path("documents").path(code).path("status").asText()).isEqualTo("reviewed");
            JsonNode source = null;
            for (JsonNode candidate : manifest.path("documents")) {
                if (candidate.path("document_id").asText().equals(code)) source = candidate;
            }
            assertThat(source).isNotNull();
            assertThat(hash(Files.readAllBytes(data.resolve(source.path("raw_file").asText()))))
                    .isEqualTo(lock.path("documents").path(code).path("raw_sha256").asText());
            KbVersion version = new KbVersion(versionId, 1, article.path("title").asText(),
                    article.path("summary").asText(), content, 1L, Instant.EPOCH);
            ReflectionTestUtils.setField(version, "id", versionId++);
            version.setStatus(KbVersionStatus.PUBLISHED);
            int before = captured.size();
            // Use production chunk construction too; do not reimplement its heading rules.
            ReflectionTestUtils.invokeMethod(kb, "buildAndSaveChunks", version, Instant.EPOCH);
            String[] expected = content.split("\n\n");
            assertThat(captured.size() - before).isEqualTo(expected.length);
            for (int i = before; i < captured.size(); i++) {
                KbChunk chunk = captured.get(i);
                assertThat(chunk.getContent()).isEqualTo(expected[i - before]);
                ReflectionTestUtils.setField(chunk, "id", fixtureId);
                keys.put(fixtureId++, code + "#" + chunk.getChunkIndex());
            }
        }
        assertThat(captured).hasSize(18);
        List<KbChunk> canonical = List.copyOf(captured);
        List<KbChunk> reversed = new ArrayList<>(canonical);
        Collections.reverse(reversed);
        AiHelpdeskFacade facade = new AiHelpdeskFacade(null, repository, null, null, null, null, null, generator, clock);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (JsonNode item : cases) {
            String query = item.path("question").asText().trim();
            assertThat(query).isNotBlank();
            List<List<String>> groups = new ArrayList<>();
            for (JsonNode group : item.path("required_evidence_groups")) groups.add(strings(group));
            // Ground-truth answers/evidence are used for scoring only, never passed to retrieval.
            when(repository.findAllActivePublishedChunks()).thenReturn(canonical);
            List<ScoredChunk> top = retrieve(facade, query, TOP_K);
            List<ScoredChunk> eligible = retrieve(facade, query, canonical.size());
            List<String> topKeys = top.stream().map(sc -> keys.get(sc.chunk().getId())).toList();
            Boolean blocked = ReflectionTestUtils.invokeMethod(facade, "isPromptInjection", query);
            List<String> afterGuard = Boolean.TRUE.equals(blocked) ? List.of() : topKeys;
            when(repository.findAllActivePublishedChunks()).thenReturn(reversed);
            List<String> reverseKeys = retrieve(facade, query, TOP_K).stream().map(sc -> keys.get(sc.chunk().getId())).toList();

            Map<String, Object> row = new LinkedHashMap<>();
            for (String field : List.of("id", "question", "expected_action", "category", "difficulty", "surface_form", "reason_code")) row.put(field, item.path(field).asText());
            row.put("required_evidence_groups", groups);
            row.put("guard_blocked", Boolean.TRUE.equals(blocked));
            row.put("query_terms", terms(facade, query).stream().sorted().toList());
            row.put("eligible_candidate_count", eligible.size());
            Map<String, Integer> expectedRanks = new LinkedHashMap<>();
            for (List<String> group : groups) {
                for (String expectedKey : group) {
                    int rank = 0;
                    for (int i = 0; i < eligible.size(); i++) {
                        if (expectedKey.equals(keys.get(eligible.get(i).chunk().getId()))) { rank = i + 1; break; }
                    }
                    expectedRanks.put(expectedKey, rank);
                }
            }
            row.put("expected_chunk_ranks_before_topk", expectedRanks);
            row.put("retrieved_before_guard", descriptions(facade, query, top, keys));
            row.put("retrieved_after_guard_keys", afterGuard);
            row.put("raw_score", RetrievalEvaluationMetrics.score(groups, topKeys));
            row.put("after_guard_score", RetrievalEvaluationMetrics.score(groups, afterGuard));
            row.put("reversed_repository_order_keys", reverseKeys);
            row.put("reversed_order_score", RetrievalEvaluationMetrics.score(groups, Boolean.TRUE.equals(blocked) ? List.of() : reverseKeys));
            row.put("membership_changes_on_reverse", !Set.copyOf(topKeys).equals(Set.copyOf(reverseKeys)));
            boolean cutoffTie = eligible.size() > TOP_K && eligible.get(TOP_K - 1).relevanceScore().compareTo(eligible.get(TOP_K).relevanceScore()) == 0;
            row.put("cutoff_tie", cutoffTie);
            if (cutoffTie) {
                var score = eligible.get(TOP_K - 1).relevanceScore();
                row.put("cutoff_score", score);
                row.put("all_keys_at_cutoff_score", eligible.stream().filter(sc -> sc.relevanceScore().compareTo(score) == 0).map(sc -> keys.get(sc.chunk().getId())).toList());
            }
            rows.add(row);
        }
        verifyNoInteractions(generator);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("run_at", Instant.now().toString());
        result.put("mode", "offline_actual_java_methods_with_frozen_published_repository_pool");
        result.put("top_k", TOP_K);
        result.put("min_relevance", ReflectionTestUtils.getField(facade, "MIN_RELEVANCE"));
        result.put("corpus_chunks", canonical.size());
        result.put("dataset_sha256", hash(Files.readAllBytes(evaluation.resolve("test_cases.jsonl"))));
        result.put("source_lock", lock);
        Map<String, String> implementationHashes = new LinkedHashMap<>();
        for (String relative : List.of("backend/src/main/java/com/helpdesk/modules/knowledge/service/AiHelpdeskFacade.java", "backend/src/main/java/com/helpdesk/modules/knowledge/service/KnowledgeBaseService.java", "backend/src/main/java/com/helpdesk/modules/knowledge/repository/KbChunkRepository.java")) {
            implementationHashes.put(relative, hash(Files.readAllBytes(root.resolve(relative))));
        }
        result.put("production_source_sha256", implementationHashes);
        result.put("repository_order", "article JSONL order then production chunk_index; live JPQL has no ORDER BY, so exact tie ordering is not guaranteed");
        result.put("limitations", List.of("No database query/external API/LLM is executed; repository pool is the 18 frozen reviewed published chunks only.",
                "After-guard gate describes retrieval eligibility, not the final fallback, answer correctness, or clarification behavior.",
                "Reversed order is a sensitivity check, not a proof of global best/worst tie ordering."));
        result.put("cases", rows);
        if (Boolean.getBoolean("helpdesk.retrieval.writeReport")) {
            Path output = evaluation.resolve(System.getProperty("helpdesk.retrieval.reportName", "retrieval-baseline") + ".json");
            Files.writeString(output, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(result) + "\n", StandardCharsets.UTF_8);
            System.out.println("Wrote retrieval baseline: " + output);
        }
        assertThat(rows).hasSize(50);
    }

    private static List<Map<String, Object>> descriptions(AiHelpdeskFacade facade, String query, List<ScoredChunk> results, Map<Long, String> keys) {
        List<Map<String, Object>> descriptions = new ArrayList<>();
        Set<String> queryTerms = terms(facade, query);
        for (ScoredChunk result : results) {
            KbChunk chunk = result.chunk();
            Set<String> words = terms(facade, (chunk.getHeading() == null ? "" : chunk.getHeading() + " ") + chunk.getContent());
            Map<String, Object> description = new LinkedHashMap<>();
            description.put("key", keys.get(chunk.getId()));
            description.put("score", result.relevanceScore());
            description.put("heading", chunk.getHeading());
            description.put("matched_query_terms", queryTerms.stream().filter(words::contains).sorted().toList());
            descriptions.add(description);
        }
        return descriptions;
    }

    private static Set<String> terms(AiHelpdeskFacade facade, String value) {
        return ReflectionTestUtils.invokeMethod(facade, "tokenize", value);
    }

    private static List<ScoredChunk> retrieve(AiHelpdeskFacade facade, String query, int count) {
        return ReflectionTestUtils.invokeMethod(facade, "retrieveActivePublishedChunks", query, count);
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }

    private static List<JsonNode> jsonLines(Path path) throws Exception {
        List<JsonNode> values = new ArrayList<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) if (!line.isBlank()) values.add(JSON.readTree(line));
        return values;
    }

    private static JsonNode read(Path path) throws Exception { return JSON.readTree(Files.readString(path, StandardCharsets.UTF_8)); }
    private static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }

    private static Path repositoryRoot() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.isRegularFile(path.resolve("data/cellphones/kb/articles.jsonl"))) path = path.getParent();
        if (path == null) throw new IllegalStateException("Run from this repository checkout");
        return path;
    }
}
