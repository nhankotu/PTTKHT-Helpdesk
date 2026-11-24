"""Validate a frozen evaluation corpus; never call APIs or modify the KB."""
from collections import Counter
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / 'data/cellphones'
EVALUATION = DATA / 'evaluation'


def sha(text):
    return hashlib.sha256(text.encode('utf-8')).hexdigest()


def load_sources():
    manifest = json.loads((DATA/'sources.json').read_text(encoding='utf-8'))
    review = json.loads((DATA/'review.json').read_text(encoding='utf-8'))
    sources = {}
    for line in (DATA/'kb/articles.jsonl').read_text(encoding='utf-8').splitlines():
        article = json.loads(line)
        code = article['articleCode']
        meta = next(d for d in manifest['documents'] if d['document_id'] == code)
        approved = review['documents'][code]
        payload_hash = sha(json.dumps(article, ensure_ascii=False, sort_keys=True, separators=(',', ':')))
        if approved['status'] != 'reviewed' or approved['payload_sha256'] != payload_hash or approved['content_sha256'] != sha(article['content']):
            raise ValueError(f'{code}: KB payload is not the reviewed version')
        if hashlib.sha256((DATA/meta['raw_file']).read_bytes()).hexdigest() != approved['raw_sha256']:
            raise ValueError(f'{code}: raw snapshot differs')
        sources[code] = {'source_url': meta['source_url'], 'raw_sha256': approved['raw_sha256'],
                         'content_sha256': sha(article['content']), 'chunks': article['content'].split('\n\n')}
    return sources


def validate_case(case, sources):
    errors = []
    for key in ('id', 'question', 'reference_answer', 'category', 'difficulty', 'surface_form', 'family_id', 'reason_code'):
        if not isinstance(case.get(key), str) or not case[key].strip():
            errors.append(f'{key}: nonempty string required')
    action = case.get('expected_action')
    if action not in ('ANSWER', 'ASK_CLARIFICATION', 'FALLBACK'):
        errors.append('unknown expected_action')
    for key in ('required_facts', 'must_not_claim', 'clarification_fields'):
        if not isinstance(case.get(key), list) or any(not isinstance(item, str) or not item.strip() for item in case.get(key, [])):
            errors.append(f'{key}: string list required')
    if not case.get('required_facts') or not case.get('must_not_claim'):
        errors.append('required_facts/must_not_claim must not be empty')
    if action == 'ASK_CLARIFICATION' and not case.get('clarification_fields'):
        errors.append('clarification must name missing fields')
    if action != 'ASK_CLARIFICATION' and case.get('clarification_fields'):
        errors.append('only clarification cases may require missing fields')
    evidence = case.get('evidence', [])
    groups = case.get('required_evidence_groups', [])
    if action in ('ANSWER', 'ASK_CLARIFICATION') and not evidence:
        errors.append('supported/clarification cases require evidence')
    if action == 'FALLBACK' and (evidence or groups):
        errors.append('fallback must not invent a supporting KB citation')
    if action == 'FALLBACK' and case.get('reason_code') == 'SUPPORTED':
        errors.append('fallback requires an unsupported/guardrail reason')
    keys = set()
    for ev in evidence:
        code, index = ev.get('document_id'), ev.get('chunk_index')
        if code not in sources or not isinstance(index, int) or not 0 <= index < len(sources[code]['chunks']):
            errors.append('invalid document/chunk reference')
            continue
        source, chunk = sources[code], sources[code]['chunks'][index]
        keys.add(f'{code}#{index}')
        expected = {'source_url': source['source_url'], 'source_snapshot_sha256': source['raw_sha256'],
                    'source_content_sha256': source['content_sha256'], 'chunk_sha256': sha(chunk)}
        for field, value in expected.items():
            if ev.get(field) != value:
                errors.append(f'{field}: source mismatch')
        excerpts = ev.get('excerpts', [])
        if not excerpts or any(not isinstance(text, str) or not text.strip() or text not in chunk for text in excerpts):
            errors.append('evidence excerpt not found in approved chunk')
    if action != 'FALLBACK' and not groups:
        errors.append('required evidence groups missing')
    for group in groups:
        if not isinstance(group, list) or not group or any(key not in keys for key in group):
            errors.append('unresolved required evidence group')
    return errors


def main():
    sources = load_sources()
    cases_path = EVALUATION/'test_cases.jsonl'
    cases = [json.loads(line) for line in cases_path.read_text(encoding='utf-8').splitlines() if line.strip()]
    errors = []
    if len(cases) != 50:
        errors.append(f'expected 50 cases; found {len(cases)}')
    ids = [case.get('id') for case in cases]
    if ids != [f'CPS-QA-{i:03}' for i in range(1, 51)]:
        errors.append('IDs must be CPS-QA-001 through CPS-QA-050 in order')
    questions = [re.sub(r'\s+', ' ', case.get('question', '').strip().casefold()) for case in cases]
    if len(questions) != len(set(questions)):
        errors.append('duplicate question')
    actions = dict(Counter(case.get('expected_action') for case in cases))
    if actions != {'ANSWER': 30, 'ASK_CLARIFICATION': 10, 'FALLBACK': 10}:
        errors.append('unexpected action distribution')
    for case in cases:
        errors.extend(f'{case.get("id")}: {error}' for error in validate_case(case, sources))
    lock = json.loads((EVALUATION/'source_lock.json').read_text(encoding='utf-8'))
    actual = {code: {'raw_sha256': source['raw_sha256'], 'content_sha256': source['content_sha256']} for code, source in sources.items()}
    if lock['documents'] != actual:
        errors.append('dataset source lock mismatch; do not reuse labels with changed policy')
    coverage = sorted({f'{ev["document_id"]}#{ev["chunk_index"]}' for case in cases for ev in case['evidence']})
    total_chunks = sum(len(s['chunks']) for s in sources.values())
    if len(coverage) != total_chunks:
        errors.append('some approved chunks have no test evidence')
    report = {'validated_at': datetime.now(timezone.utc).isoformat(), 'dataset_sha256': hashlib.sha256(cases_path.read_bytes()).hexdigest(),
              'cases': len(cases), 'actions': actions, 'surface_forms': dict(Counter(c['surface_form'] for c in cases)),
              'covered_chunks': len(coverage), 'approved_chunks': total_chunks, 'evidence_chunk_keys': coverage,
              'errors': errors, 'passed': not errors,
              'meaning': 'Data structure, source integrity and citation locations only. Not an executed RAG quality evaluation.'}
    (EVALUATION/'validation_report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({key: report[key] for key in ('cases', 'actions', 'covered_chunks', 'approved_chunks', 'passed', 'errors')}, ensure_ascii=False))
    if errors:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
