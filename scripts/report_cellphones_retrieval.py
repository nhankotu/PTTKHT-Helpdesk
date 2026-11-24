"""Summarize actual JVM retrieval output. No search implementation or model calls here."""
from collections import defaultdict
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EVAL = ROOT/'data/cellphones/evaluation'


def ratio(numerator, denominator):
    return numerator/denominator if denominator else None


def aggregate(rows, score_key='after_guard_score', candidates_key='retrieved_after_guard_keys'):
    supported = [r for r in rows if r['expected_action'] != 'FALLBACK']
    negatives = [r for r in rows if r['expected_action'] == 'FALLBACK']
    complete = sum(bool(r[score_key]['complete']) for r in supported)
    any_hit = sum(r[score_key]['firstRelevantRank'] > 0 for r in supported)
    groups = sum(r[score_key]['requiredGroups'] for r in supported)
    hit_groups = sum(r[score_key]['hitGroups'] for r in supported)
    return {'supported_cases': len(supported), 'complete_evidence_cases': complete,
            'complete_evidence_rate': ratio(complete, len(supported)),
            'any_evidence_cases': any_hit, 'any_evidence_rate': ratio(any_hit, len(supported)),
            'required_evidence_groups': groups, 'hit_evidence_groups': hit_groups,
            'evidence_group_recall': ratio(hit_groups, groups),
            'mrr_first_relevant': ratio(sum(1/r[score_key]['firstRelevantRank'] if r[score_key]['firstRelevantRank'] else 0 for r in supported), len(supported)),
            'fallback_cases': len(negatives), 'fallback_guard_blocked': sum(r['guard_blocked'] for r in negatives),
            'fallback_with_candidates': sum(bool(r[candidates_key]) for r in negatives),
            'fallback_without_candidates': sum(not r[candidates_key] for r in negatives)}


def percent(value):
    return 'không áp dụng' if value is None else f'{value*100:.2f}%'


def write_report(report_name='retrieval-baseline'):
    path = EVAL/f'{report_name}.json'
    report = json.loads(path.read_text(encoding='utf-8'))
    dataset_path = EVAL/'test_cases.jsonl'
    if hashlib.sha256(dataset_path.read_bytes()).hexdigest() != report['dataset_sha256']:
        raise ValueError('Questions changed after the JVM run; rerun the benchmark')
    for relative, expected in report['production_source_sha256'].items():
        if hashlib.sha256((ROOT/relative).read_bytes()).hexdigest() != expected:
            raise ValueError(f'Production implementation changed since benchmark: {relative}')
    cases = report['cases']
    if len(cases) != 50 or len({r['id'] for r in cases}) != 50:
        raise ValueError('Incomplete/duplicate case output')
    # Alternative ordering is a separate sensitivity scenario, not a new ground truth.
    reverse_rows = [{**r, 'retrieved_after_guard_keys': [] if r['guard_blocked'] else r['reversed_repository_order_keys']} for r in cases]
    summary = aggregate(cases)
    reverse_summary = aggregate(reverse_rows, score_key='reversed_order_score')
    breakdowns = {}
    for dimension in ('expected_action', 'surface_form', 'difficulty'):
        buckets = defaultdict(list)
        for row in cases:
            buckets[row[dimension]].append(row)
        breakdowns[dimension] = {name: aggregate(rows) for name, rows in sorted(buckets.items())}
    missed = [r for r in cases if r['expected_action'] != 'FALLBACK' and not r['after_guard_score']['complete']]
    summary_record = {'run_at': report['run_at'], 'dataset_sha256': report['dataset_sha256'], 'top_k': report['top_k'],
                      'min_relevance': report['min_relevance'], 'mode': report['mode'], 'summary': summary,
                      'breakdowns': breakdowns, 'reversed_repository_order_summary': reverse_summary,
                      'cutoff_tie_cases': [r['id'] for r in cases if r['cutoff_tie']],
                      'membership_changes_on_reverse': [r['id'] for r in cases if r['membership_changes_on_reverse']],
                      'complete_score_changes_on_reverse': [r['id'] for r in cases if r['expected_action'] != 'FALLBACK' and r['after_guard_score']['complete'] != r['reversed_order_score']['complete']],
                      'missing_complete_evidence_ids': [r['id'] for r in missed],
                      'limitations': report['limitations']}
    summary_name = 'retrieval-summary' if report_name == 'retrieval-baseline' else f'{report_name}-summary'
    (EVAL/f'{summary_name}.json').write_text(json.dumps(summary_record, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    lines = [f'# Kết quả truy xuất KB CellphoneS — {report_name}', '',
             f'Chạy lúc `{report["run_at"]}`, 50 câu, {report["corpus_chunks"]} chunk đã duyệt, top-K={report["top_k"]}, ngưỡng={report["min_relevance"]}.', '',
             '**Đây là đánh giá truy xuất offline bằng hàm Java thực tế, không phải điểm trả lời của Gemini hay kiểm thử API/database đầy đủ.**', '',
             '## Kết quả chính', '',
             '| Chỉ số | Kết quả |', '|---|---|',
             f'| Tìm đủ mọi nhóm bằng chứng trong 40 câu có nguồn | {summary["complete_evidence_cases"]}/{summary["supported_cases"]} ({percent(summary["complete_evidence_rate"])}) |',
             f'| Tìm ít nhất một đoạn đúng trong 40 câu có nguồn | {summary["any_evidence_cases"]}/{summary["supported_cases"]} ({percent(summary["any_evidence_rate"])}) |',
             f'| Recall theo nhóm bằng chứng | {summary["hit_evidence_groups"]}/{summary["required_evidence_groups"]} ({percent(summary["evidence_group_recall"])}) |',
             f'| MRR của đoạn liên quan đầu tiên (40 câu) | {summary["mrr_first_relevant"]:.4f} |',
             f'| Câu fallback vẫn được trả tài liệu sau guard | {summary["fallback_with_candidates"]}/{summary["fallback_cases"]} |',
             f'| Câu fallback bị guard chặn | {summary["fallback_guard_blocked"]}/{summary["fallback_cases"]} |', '',
             'Tìm được một đoạn đúng chưa đủ với câu cần nhiều nguồn: mọi nhóm yêu cầu phải có ít nhất một chunk trong top-K. MRR đo vị trí của đoạn đúng đầu tiên, không chứng minh đủ các đoạn còn lại.', '',
             'Các câu fallback không có nguồn bị loại khỏi mẫu số recall. Có tài liệu trả về cho câu fallback là dấu hiệu rủi ro ở tầng truy xuất, **chưa chứng minh model sẽ trả lời sai**; model/guard phía sau có thể còn từ chối. Không có tài liệu cũng chưa chứng minh hệ thống xử lý hội thoại đúng.', '',
             '## Theo nhóm câu hỏi', '', '| Nhóm | Câu có nguồn | Đủ bằng chứng | Recall nhóm |', '|---|---:|---:|---:|']
    for dimension in ('expected_action', 'surface_form'):
        for name, metric in breakdowns[dimension].items():
            if metric['supported_cases']:
                lines.append(f'| {dimension}: {name} | {metric["supported_cases"]} | {metric["complete_evidence_cases"]}/{metric["supported_cases"]} | {percent(metric["evidence_group_recall"])} |')
    lines.extend(['', '## Các câu chưa tìm đủ nguồn', ''])
    for row in missed:
        top = {c['key'] for c in row['retrieved_before_guard']}
        missing_groups = [group for group in row['required_evidence_groups'] if not top.intersection(group)]
        ranks = row.get('expected_chunk_ranks_before_topk', {})
        detail = '; '.join(f'{key}: ' + (f'hạng {ranks[key]}' if ranks.get(key) else 'không qua ngưỡng') for group in missing_groups for key in group)
        lines.extend([f'- **{row["id"]}** — {row["question"]}', f'  Thiếu: {detail}.'])
    lines.extend(['', '## Thứ tự hòa điểm', '',
                  f'Có {len(summary_record["cutoff_tie_cases"])} câu hòa điểm tại biên top-{report["top_k"]}; đảo thứ tự repository làm đổi tập ứng viên ở {len(summary_record["membership_changes_on_reverse"])} câu.',
                  f'Trong lượt đảo thứ tự, số câu đủ nguồn là {reverse_summary["complete_evidence_cases"]}/{reverse_summary["supported_cases"]}. Các câu đổi kết quả đủ/thiếu: {", ".join(summary_record["complete_score_changes_on_reverse"]) or "không có"}.', '',
                  'JPQL production chưa có ORDER BY. Lượt chính dùng thứ tự bài trong JSONL rồi chunk_index; đây là một thứ tự kiểm thử cố định, không khẳng định là thứ tự database live. Lượt đảo thứ tự chỉ là một phép kiểm tra nhạy cảm, không phải chứng minh biên tốt nhất/xấu nhất.', '',
                  '## Phương pháp và giới hạn', '',
                  '- Gọi trực tiếp `KnowledgeBaseService.buildAndSaveChunks` và `AiHelpdeskFacade.retrieveActivePublishedChunks`, `tokenize`, `isPromptInjection` bằng reflection trong JVM.',
                  '- Repository được mock chỉ ở ranh giới cấp 18 chunk từ hai bài đã duyệt; hash content và raw snapshot được kiểm tra. Không chạy truy vấn SQL/lọc quyền hay kiểm thử dữ liệu mới thu hồi.',
                  '- Chỉ `question.trim()` là query, tương ứng customQuery. Không đưa đáp án, dẫn chứng kỳ vọng hay tiêu chí chấm vào truy xuất. Chưa đánh giá query ghép từ cả lịch sử ticket.',
                  '- Giữ nguyên cách tách Unicode, chữ thường, điểm trùng từ, làm tròn 4 chữ số, ngưỡng và thứ tự hòa điểm của code production. Không chuẩn hóa dấu hoặc thêm từ đồng nghĩa cho lượt baseline.',
                  '- Guard được đo riêng trước retrieval; chưa gọi generator, tạo draft, lưu ai_run/citation hay gửi trả lời. `verifyNoInteractions(generator)` xác nhận không gọi LLM.',
                  '- Mẫu 50 câu do dự án biên soạn là baseline nhỏ; chưa chứng minh tổng quát hóa sang bộ dữ liệu độc lập. Không chỉnh ngưỡng rồi chấm lại trên cùng tập mà coi là đánh giá độc lập.', '',
                  '## Chi tiết 50 câu', ''])
    for row in cases:
        score = row['after_guard_score']
        label = 'Đủ nguồn' if score['complete'] else 'Thiếu nguồn'
        if row['expected_action'] == 'FALLBACK':
            label = 'Guard chặn' if row['guard_blocked'] else 'Có ứng viên, cần hậu kiểm' if row['retrieved_after_guard_keys'] else 'Không có ứng viên'
        lines.extend([f'### {row["id"]} — {label}', '', f'**Câu hỏi:** {row["question"]}', '', f'**Nhãn kỳ vọng:** `{row["expected_action"]}`; lý do `{row["reason_code"]}`.', '',
                      '**Nhóm bằng chứng cần:** ' + (' VÀ '.join('(' + ' HOẶC '.join(g) + ')' for g in row['required_evidence_groups']) or 'Không có nguồn trả lời trong KB đã duyệt.'), '',
                      f'**Guard chặn:** {"Có" if row["guard_blocked"] else "Không"}. **Số ứng viên qua ngưỡng trước top-K:** {row["eligible_candidate_count"]}.', '',
                      '| Hạng | Chunk | Điểm | Từ trong query trùng |', '|---:|---|---:|---|'])
        for rank, candidate in enumerate(row['retrieved_before_guard'], 1):
            terms = ', '.join(candidate['matched_query_terms']).replace('|', '\\|')
            lines.append(f'| {rank} | {candidate["key"]} | {candidate["score"]:.4f} | {terms} |')
        lines.extend(['', '**Sau guard:** ' + (', '.join(row['retrieved_after_guard_keys']) or 'Không có ứng viên được chuyển tiếp.'), '',
                      '**Đảo thứ tự repository:** ' + (', '.join(row['reversed_repository_order_keys']) or 'Không có ứng viên.'), ''])
    lines.extend(['## Nhận định cho bước tiếp theo', '',
                  'Các lỗi quan sát được tập trung ở câu không dấu/viết tắt, câu hỏi ngắn về ship và câu cần nhiều đoạn. Code chưa chuẩn hóa dấu hay từ đồng nghĩa, trong khi từ phổ biến và URL nguồn cũng tham gia tính điểm. Nên kiểm tra các yếu tố đó bằng tập dev riêng trước khi thay đổi retrieval.', '',
                  'Các câu ngoài phạm vi, yêu cầu dữ liệu đơn hàng hoặc mục hoàn tiền bị loại vẫn có thể trùng từ với nhiều chính sách. Cần kiểm tra khả năng trả lời/phạm vi/nguồn và dữ liệu trực tiếp ở tầng sau; không coi vượt ngưỡng keyword là đủ căn cứ để trả lời.', '',
                  'Báo cáo đo truy xuất tại top-K đã ghi ở đầu; chưa bật Gemini và chưa chấm chất lượng câu trả lời. Generator mock mặc định chỉ dùng 3 đoạn đầu, nên đủ nguồn trong tập truy xuất chưa chứng minh câu trả lời dùng đủ nguồn.'])
    (EVAL/f'{report_name}.md').write_text('\n'.join(lines)+'\n', encoding='utf-8')
    print(json.dumps(summary, ensure_ascii=False))


if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--report-name', choices=('retrieval-baseline', 'retrieval-top10'), default='retrieval-baseline')
    write_report(parser.parse_args().report_name)
