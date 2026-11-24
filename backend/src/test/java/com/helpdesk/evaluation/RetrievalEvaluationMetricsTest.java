package com.helpdesk.evaluation;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class RetrievalEvaluationMetricsTest {
    @Test
    void findingOneRequiredGroupMustNotCountAsCompleteEvidence() {
        var score = RetrievalEvaluationMetrics.score(List.of(List.of("A"), List.of("B")), List.of("X", "A"));
        assertThat(score.requiredGroups()).isEqualTo(2);
        assertThat(score.hitGroups()).isEqualTo(1);
        assertThat(score.complete()).isFalse();
        assertThat(score.firstRelevantRank()).isEqualTo(2);
    }

    @Test
    void alternativeWithinAGroupIsSufficientButAllGroupsStillRequired() {
        var score = RetrievalEvaluationMetrics.score(List.of(List.of("A", "B"), List.of("C")), List.of("B", "C"));
        assertThat(score.hitGroups()).isEqualTo(2);
        assertThat(score.complete()).isTrue();
        assertThat(score.firstRelevantRank()).isEqualTo(1);
    }

    @Test
    void fallbackWithoutEvidenceMustNotBecomeAutomaticRecallSuccess() {
        var score = RetrievalEvaluationMetrics.score(List.of(), List.of("X"));
        assertThat(score.requiredGroups()).isZero();
        assertThat(score.complete()).isFalse();
        assertThat(score.firstRelevantRank()).isZero();
    }

    @Test
    void repeatedCandidatesDoNotInflateRequiredGroupHits() {
        var score = RetrievalEvaluationMetrics.score(List.of(List.of("A"), List.of("B")), List.of("A", "A", "A"));
        assertThat(score.hitGroups()).isEqualTo(1);
        assertThat(score.complete()).isFalse();
    }
}
