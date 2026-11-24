package com.helpdesk.modules.knowledge.rag.pipeline;

import com.fasterxml.jackson.databind.*;
import com.helpdesk.common.ai.*;
import com.helpdesk.common.ai.structured.ModelAdapterRegistry;
import com.helpdesk.common.ai.structured.StructuredModelAdapter;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.math.BigDecimal;
import java.util.*;
import static com.helpdesk.modules.knowledge.rag.pipeline.RagContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RagMockPolicyTest {
    ObjectMapper json = new ObjectMapper();
    List<Evidence> pool() throws Exception {
        Path file = Path.of("../data/cellphones/kb/articles.jsonl");
        List<Evidence> result = new ArrayList<>(); long id = 1, version = 1;
        for (String row : Files.readAllLines(file)) {
            JsonNode article = json.readTree(row); String content = article.path("content").asText();
            String[] chunks = content.split("\n\n");
            for (int i=0; i<chunks.length; i++) result.add(new Evidence(id++, version, article.path("articleCode").asText(), RagGuards.hash(content), i, chunks[i].split("\n")[0], chunks[i], BigDecimal.ONE));
            version++;
        } return result;
    }
    @Test void mockAnswersReviewedCancellationWithoutCopyingSourceUrlIntoText() throws Exception {
        var registry = new PolicyRegistry(json);
        var adapters = new RagModelAdapters(json, new ModelAdapterRegistry(List.of()), new AiProperties("mock", null, null, null, null), registry, "", "", "", "");
        var input = new Input(1L, 2L, "Mình chưa nhận hàng, giờ hủy được không?", List.of(new Source("question", "Mình chưa nhận hàng, giờ hủy được không?")));
        var guards = new RagGuards(); var plan = guards.plan(input, adapters.queryPreprocessor().prepare(input));
        var required = registry.required(plan, pool());
        var draft = guards.draft(input, plan, required, adapters.groundedAnswerer().answer(input, plan, required));
        assertThat(draft.decision()).isEqualTo(Decision.ANSWER);
        assertThat(draft.text()).doesNotContain("https://");
    }
    @Test void multiIntentNeedsThreePolicySectionsAndChangedSourceInvalidatesRule() throws Exception {
        var registry = new PolicyRegistry(json); var adapters = new RagModelAdapters(json, new ModelAdapterRegistry(List.of()), new AiProperties("mock", null, null, null, null), registry, "", "", "", "");
        String question = "Máy mua online cần đổi trả: giữ hộp và phí vận chuyển thế nào?";
        var input = new Input(1L, 2L, question, List.of(new Source("question", question)));
        Plan plan = new RagGuards().plan(input, adapters.queryPreprocessor().prepare(input));
        assertThat(plan.intents()).hasSize(3);
        var required = registry.required(plan, pool()); assertThat(required).hasSize(3);
        assertThatThrownBy(() -> registry.required(plan, required.subList(0, 2))).isInstanceOf(GateFailure.class);
        var draft = new RagGuards().draft(input, plan, required, adapters.groundedAnswerer().answer(input, plan, required));
        assertThat(draft.citations()).hasSize(3);
    }
    @Test void stagesCanUseDifferentProvidersWithoutChangingGuards() throws Exception {
        var registry = new PolicyRegistry(json);
        StructuredModelAdapter fake = mock(StructuredModelAdapter.class); when(fake.provider()).thenReturn("openai-compatible");
        when(fake.generate(any())).thenReturn(new StructuredModelAdapter.Reply(json.readTree("{\"decision\":\"FALLBACK\",\"answerParts\":[],\"missingFields\":[],\"reasonCode\":\"NO_SOURCE\",\"confidence\":0}"), 40L, 10L));
        var adapters = new RagModelAdapters(json, new ModelAdapterRegistry(List.of(fake)), new AiProperties("mock", null, null, null, null), registry,
                "", "test-model", "mock", "openai-compatible");
        String question = "Mình muốn hủy đơn"; var input = new Input(1L, 2L, question, List.of(new Source("question", question)));
        Plan plan = new RagGuards().plan(input, adapters.queryPreprocessor().prepare(input));
        verify(fake, never()).generate(any());
        new RagGuards().draft(input, plan, registry.required(plan, pool()), adapters.groundedAnswerer().answer(input, plan, registry.required(plan, pool())));
        verify(fake, times(1)).generate(any());
        assertThat(input.trace().snapshot().get("model2").inputTokens()).isEqualTo(40L);
    }
    @Test void evidenceCannotBeSwappedBetweenIntents() throws Exception {
        var registry=new PolicyRegistry(json);
        var adapters=new RagModelAdapters(json,new ModelAdapterRegistry(List.of()),new AiProperties("mock",null,null,null,null),registry,"","","","");
        String question="Máy mua online cần đổi trả: giữ hộp và phí vận chuyển thế nào?";
        var input=new Input(1L,2L,question,List.of(new Source("question",question)));
        Plan plan=new RagGuards().plan(input,adapters.queryPreprocessor().prepare(input));
        List<Evidence> evidence=registry.required(plan,pool());
        var response=(com.fasterxml.jackson.databind.node.ObjectNode)adapters.groundedAnswerer().answer(input,plan,evidence);
        var parts=(com.fasterxml.jackson.databind.node.ArrayNode)response.get("answerParts");
        String first=parts.get(0).path("intentId").asText(), second=parts.get(1).path("intentId").asText();
        ((com.fasterxml.jackson.databind.node.ObjectNode)parts.get(0)).put("intentId",second);
        ((com.fasterxml.jackson.databind.node.ObjectNode)parts.get(1)).put("intentId",first);
        var draft=new RagGuards().draft(input,plan,evidence,response);
        var assembler=new EvidenceAssembler(mock(org.springframework.jdbc.core.JdbcTemplate.class),registry,new RagGuards());
        assertThat(assembler.covers(plan,draft)).isFalse();
    }
}
