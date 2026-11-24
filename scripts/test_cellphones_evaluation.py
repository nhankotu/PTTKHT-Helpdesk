import copy
import hashlib
import unittest

from validate_cellphones_evaluation import validate_case


class EvaluationValidationTests(unittest.TestCase):
    def setUp(self):
        content = '## Phí giao hàng\nPhí 15.000đ.\nNguồn: https://cellphones.com.vn/policy'
        self.sources = {'CPS-TEST': {'source_url': 'https://cellphones.com.vn/policy', 'raw_sha256': 'a'*64, 'content_sha256': hashlib.sha256(content.encode()).hexdigest(), 'chunks': [content]}}
        self.case = {'id': 'CPS-QA-001', 'question': 'Phí giao hàng bao nhiêu?', 'expected_action': 'ANSWER', 'reference_answer': 'Phí 15.000đ.', 'category': 'SHIPPING', 'difficulty': 'easy', 'surface_form': 'standard', 'family_id': 'fee', 'reason_code': 'SUPPORTED', 'required_facts': ['15.000đ'], 'must_not_claim': ['Miễn phí'], 'clarification_fields': [], 'evidence': [{'document_id': 'CPS-TEST', 'source_url': 'https://cellphones.com.vn/policy', 'source_snapshot_sha256': 'a'*64, 'source_content_sha256': self.sources['CPS-TEST']['content_sha256'], 'chunk_index': 0, 'chunk_sha256': hashlib.sha256(content.encode()).hexdigest(), 'excerpts': ['Phí 15.000đ.']}], 'required_evidence_groups': [['CPS-TEST#0']]}

    def test_valid_supported_case(self):
        self.assertEqual(validate_case(self.case, self.sources), [])

    def test_rejects_evidence_outside_approved_corpus(self):
        self.case['evidence'][0]['excerpts'] = ['Hoàn tiền sau 7 ngày.']
        self.assertTrue(validate_case(self.case, self.sources))

    def test_rejects_changed_source_and_chunk_hash(self):
        for field in ['source_snapshot_sha256', 'source_content_sha256', 'chunk_sha256']:
            case = copy.deepcopy(self.case)
            case['evidence'][0][field] = 'b'*64
            self.assertTrue(validate_case(case, self.sources))

    def test_answer_requires_evidence(self):
        self.case['evidence'] = []
        self.assertTrue(validate_case(self.case, self.sources))

    def test_clarification_requires_named_missing_fields(self):
        self.case['expected_action'] = 'ASK_CLARIFICATION'
        self.assertTrue(validate_case(self.case, self.sources))
        self.case['clarification_fields'] = ['giá trị đơn hàng']
        self.assertEqual(validate_case(self.case, self.sources), [])

    def test_fallback_must_not_fake_a_citation(self):
        self.case['expected_action'] = 'FALLBACK'
        self.case['reason_code'] = 'LIVE_DATA_REQUIRED'
        self.assertTrue(validate_case(self.case, self.sources))
        self.case['evidence'] = []
        self.case['required_evidence_groups'] = []
        self.assertEqual(validate_case(self.case, self.sources), [])

    def test_expected_evidence_groups_must_resolve_to_actual_evidence(self):
        self.case['required_evidence_groups'] = [['CPS-TEST#99']]
        self.assertTrue(validate_case(self.case, self.sources))

    def test_rejects_unknown_actions_and_blank_questions(self):
        self.case['expected_action'] = 'AUTO_APPROVE'
        self.assertTrue(validate_case(self.case, self.sources))
        self.case['expected_action'] = 'ANSWER'
        self.case['question'] = '  '
        self.assertTrue(validate_case(self.case, self.sources))


if __name__ == '__main__':
    unittest.main()
