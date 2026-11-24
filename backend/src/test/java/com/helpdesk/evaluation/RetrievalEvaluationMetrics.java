package com.helpdesk.evaluation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class RetrievalEvaluationMetrics {
    record EvidenceScore(int requiredGroups, int hitGroups, boolean complete, int firstRelevantRank) {}

    static EvidenceScore score(List<List<String>> groups, List<String> candidates) {
        Set<String> retrieved = new HashSet<>(candidates);
        int hit = (int) groups.stream().filter(group -> group.stream().anyMatch(retrieved::contains)).count();
        Set<String> relevant = new HashSet<>();
        groups.forEach(relevant::addAll);
        int firstRank = 0;
        for (int i = 0; i < candidates.size(); i++) {
            if (relevant.contains(candidates.get(i))) {
                firstRank = i + 1;
                break;
            }
        }
        return new EvidenceScore(groups.size(), hit, !groups.isEmpty() && hit == groups.size(), firstRank);
    }

    private RetrievalEvaluationMetrics() {}
}
