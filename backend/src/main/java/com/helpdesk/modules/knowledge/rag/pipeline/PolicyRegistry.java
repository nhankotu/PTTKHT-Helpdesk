package com.helpdesk.modules.knowledge.rag.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.*;
import static com.helpdesk.modules.knowledge.rag.pipeline.RagContracts.*;

/** Reviewed source bindings only. No evaluation questions or expected answers are loaded. */
@Component
public class PolicyRegistry {
    private final JsonNode rules;
    public PolicyRegistry(ObjectMapper mapper) throws IOException {
        try (var stream = new ClassPathResource("rag/cellphones-policy-v1.json").getInputStream()) { rules = mapper.readTree(stream); }
        if (!rules.isObject()) throw new IllegalStateException("Invalid policy registry");
        for (IntentCode code : IntentCode.values()) if (code != IntentCode.OTHER_POLICY && !rules.path(code.name()).isArray()) throw new IllegalStateException("Missing policy binding: " + code);
    }
    public List<Evidence> required(Plan plan, List<Evidence> pool) {
        Map<Long, Evidence> required = new LinkedHashMap<>();
        for (Intent intent : plan.intents()) {
            if (intent.code() == IntentCode.OTHER_POLICY) continue;
            for (JsonNode binding : rules.path(intent.code().name())) {
                Evidence match = pool.stream().filter(e -> e.articleCode().equals(binding.path("articleCode").asText())
                        && e.versionHash().equals(binding.path("contentHash").asText()) && e.chunkIndex() == binding.path("chunkIndex").asInt()
                        && RagGuards.hash(e.content()).equals(binding.path("chunkHash").asText())).findFirst().orElseThrow(() -> new GateFailure("RULE_SOURCE_MISMATCH"));
                required.put(match.chunkId(), match);
            }
        }
        return List.copyOf(required.values());
    }
    public List<String> missing(Input input, Plan plan) {
        // Never compute an individualized fee/eligibility from unverified customer assertions in v1.
        String question = RagGuards.fold(input.question());
        if (plan.intents().stream().anyMatch(i -> i.code() == IntentCode.SHIPPING_FEE)
                && !question.contains("chinh sach") && !question.contains("bang phi")
                && (question.contains("bao nhieu") || question.contains("tinh ship"))) {
            Set<String> present = new HashSet<>(plan.facts().stream().map(Fact::field).toList());
            return List.of("orderValue", "membershipTier").stream().filter(f -> !present.contains(f)).toList();
        }
        return List.of();
    }
}
