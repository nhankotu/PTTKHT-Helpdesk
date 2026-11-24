package com.helpdesk.modules.knowledge.rag.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpdesk.modules.knowledge.repository.RagRequestStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static com.helpdesk.modules.knowledge.rag.pipeline.RagContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RagPipelineTest {
    QueryPreprocessor first = mock(QueryPreprocessor.class);
    GroundedAnswerer second = mock(GroundedAnswerer.class);
    EvidenceAssembler evidence = mock(EvidenceAssembler.class);
    RagRequestStore requests = mock(RagRequestStore.class);
    ObjectMapper json = new ObjectMapper();
    Input input = new Input(1L, 2L, "Chính sách hỗ trợ", List.of(new Source("question", "Chính sách hỗ trợ")));
    RagPipeline pipeline;
    @BeforeEach void setup() {
        pipeline = new RagPipeline(new RagGuards(), first, second, evidence, requests);
        when(requests.begin(anyLong(), anyString(), anyString())).thenReturn(new RagRequestStore.Snapshot(true, null));
        when(requests.claim(anyLong(), anyString(), anyInt())).thenReturn(true);
    }
    private void validFirst() throws Exception {
        when(first.prepare(any())).thenReturn(json.readTree("""
          {"decision":"READY","normalizedQuestion":"Chính sách hỗ trợ","intents":[{"id":"i1","code":"OTHER_POLICY","query":"Chính sách hỗ trợ","factRefs":[]}],"facts":[],"missingFields":[],"ambiguities":[],"confidence":0.95}
          """));
    }
    @Test void invalidFirstOrTimeoutNeverCallsSecondAndNeverRetries() throws Exception {
        when(first.prepare(any())).thenThrow(new IllegalStateException("timeout"));
        Execution result = pipeline.execute(input, 10, "one");
        assertThat(result.draft().decision()).isEqualTo(Decision.FAILED);
        verify(first, times(1)).prepare(any()); verifyNoInteractions(second, evidence);
    }
    @Test void noEvidenceDoesNotCallAnswerModel() throws Exception {
        validFirst(); when(evidence.assemble(any(), any(), anyInt())).thenReturn(List.of());
        assertThat(pipeline.execute(input, 10, "two").draft().reason()).isEqualTo("NO_RELEVANT_KB");
        verify(first, times(1)).prepare(any()); verifyNoInteractions(second);
    }
    @Test void clarificationSkipsRetrievalAndAnswerModel() throws Exception {
        when(first.prepare(any())).thenReturn(json.readTree("""
          {"decision":"ASK_CLARIFICATION","normalizedQuestion":"Chính sách hỗ trợ","intents":[],"facts":[],"missingFields":["orderValue"],"ambiguities":[],"confidence":0.9}
          """));
        Draft draft = pipeline.execute(input, 10, "three").draft();
        assertThat(draft.decision()).isEqualTo(Decision.ASK_CLARIFICATION);
        assertThat(draft.text()).contains("Giá trị đơn hàng"); verifyNoInteractions(second, evidence);
    }
    @Test void duplicateOrAbandonedRequestNeverCallsEitherModel() {
        when(requests.begin(anyLong(), anyString(), anyString())).thenReturn(new RagRequestStore.Snapshot(false, 99L));
        assertThat(pipeline.execute(input, 10, "done").cachedRunId()).isEqualTo(99L);
        when(requests.begin(anyLong(), anyString(), anyString())).thenReturn(new RagRequestStore.Snapshot(false, null));
        assertThatThrownBy(() -> pipeline.execute(input, 10, "busy")).isInstanceOf(com.helpdesk.common.exception.BusinessException.class);
        verifyNoInteractions(first, second, evidence);
    }
    @Test void successfulDraftUsesEachModelOnceAndRechecksEvidence() throws Exception {
        validFirst();
        Evidence e = new Evidence(10, 20, "KB", "hash", 0, "Hỗ trợ", "Chính sách hỗ trợ khách hàng tại cửa hàng.", java.math.BigDecimal.ONE);
        when(evidence.assemble(any(), any(), anyInt())).thenReturn(List.of(e));
        when(evidence.current(any())).thenReturn(true);
        when(evidence.covers(any(), any())).thenReturn(true);
        when(second.answer(any(), any(), any())).thenReturn(json.readTree("""
          {"decision":"ANSWER","answerParts":[{"intentId":"i1","text":"Chính sách hỗ trợ khách hàng tại cửa hàng.","supports":[{"chunkId":10,"versionId":20,"quote":"Chính sách hỗ trợ khách hàng tại cửa hàng."}]}],"missingFields":[],"reasonCode":"SUPPORTED","confidence":0.9}
          """));
        assertThat(pipeline.execute(input, 10, "ok").draft().decision()).isEqualTo(Decision.ANSWER);
        verify(first, times(1)).prepare(any()); verify(second, times(1)).answer(any(), any(), any());
        verify(evidence, atLeastOnce()).current(any());
    }
    @Test void revokedEvidenceAfterModel2DoesNotReturnDraft() throws Exception {
        validFirst();
        Evidence e = new Evidence(10, 20, "KB", "hash", 0, "Hỗ trợ", "Chính sách hỗ trợ khách hàng tại cửa hàng.", java.math.BigDecimal.ONE);
        Evidence uncited = new Evidence(11,21,"KB-OTHER","other",0,"Nguồn bổ sung","Chính sách bổ sung không được cite.",java.math.BigDecimal.ONE);
        when(evidence.assemble(any(), any(), anyInt())).thenReturn(List.of(e,uncited));
        when(evidence.current(any())).thenReturn(true, false); when(evidence.covers(any(), any())).thenReturn(true);
        when(second.answer(any(), any(), any())).thenReturn(json.readTree("""
          {"decision":"ANSWER","answerParts":[{"intentId":"i1","text":"Chính sách hỗ trợ khách hàng tại cửa hàng.","supports":[{"chunkId":10,"versionId":20,"quote":"Chính sách hỗ trợ khách hàng tại cửa hàng."}]}],"missingFields":[],"reasonCode":"SUPPORTED","confidence":0.9}
          """));
        Draft draft = pipeline.execute(input, 10, "revoked").draft();
        assertThat(draft.reason()).isEqualTo("OUTDATED_OR_REVOKED_KB"); assertThat(draft.text()).isNull();
        verify(second, times(1)).answer(any(), any(), any());
        verify(evidence,times(2)).current(argThat(context -> context.size()==2));
    }
}
