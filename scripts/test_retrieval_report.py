import unittest
from report_cellphones_retrieval import aggregate


def row(action, hit, required, rank, candidates, complete=False):
    return {'expected_action': action, 'after_guard_score': {'requiredGroups': required, 'hitGroups': hit, 'complete': complete, 'firstRelevantRank': rank}, 'retrieved_after_guard_keys': candidates, 'guard_blocked': False}


class RetrievalReportTests(unittest.TestCase):
    def test_negative_questions_do_not_inflate_recall_denominator(self):
        result = aggregate([row('ANSWER', 1, 1, 1, ['A'], True), row('ASK_CLARIFICATION', 1, 2, 2, ['B']), row('FALLBACK', 0, 0, 0, ['X'])])
        self.assertEqual(result['supported_cases'], 2)
        self.assertEqual(result['complete_evidence_cases'], 1)
        self.assertEqual(result['complete_evidence_rate'], 0.5)
        self.assertAlmostEqual(result['evidence_group_recall'], 2/3)
        self.assertAlmostEqual(result['mrr_first_relevant'], 0.75)
        self.assertEqual(result['fallback_cases'], 1)
        self.assertEqual(result['fallback_with_candidates'], 1)

    def test_no_hit_is_zero_reciprocal_rank_not_division_by_zero(self):
        result = aggregate([row('ANSWER', 0, 1, 0, [])])
        self.assertEqual(result['mrr_first_relevant'], 0)
        self.assertEqual(result['any_evidence_cases'], 0)
        self.assertEqual(result['evidence_group_recall'], 0)

    def test_no_supported_cases_have_undefined_recall_not_perfect_recall(self):
        result = aggregate([row('FALLBACK', 0, 0, 0, [])])
        self.assertIsNone(result['evidence_group_recall'])
        self.assertIsNone(result['complete_evidence_rate'])
        self.assertEqual(result['fallback_without_candidates'], 1)


if __name__ == '__main__':
    unittest.main()
