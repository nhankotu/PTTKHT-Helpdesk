# CellphoneS evaluation set — step 1

User approved creating the first 50 Vietnamese test questions against the already reviewed corpus. No retrieval/LLM evaluation, backend changes or KB writes in this step.

- [x] Author 30 answerable cases, 10 clarification cases and 10 fallback cases. Include paraphrases, missing accents/typos, table/exception reasoning, cross-chunk evidence, excluded refund policies and requests requiring live data.
- [x] Attach exact excerpts from approved article payloads, stable chunk indices/hashes and raw/content source hashes. Do not cite excluded policy sections or image content. Pin the frozen source version; do not browse/update policy during generation.
- [x] Validate 50 unique IDs/questions, distributions, evidence groups, all hashes and citation substring matches. Write meaningful validator tests before implementation.
- [x] Export JSONL plus readable Markdown, instructions/scoring rubric and validation report. Review answer meaning independently. Keep all evaluation files separate from KB import paths.

Results: 50 records, 30/10/10 actions, coverage of 18/18 approved chunks, zero data-validation errors. Eight new validator tests observed RED then GREEN; combined collection/evaluation suite 18 tests. Independent semantic review of all 50 by review_cellphones_qa: no blockers; redundant request for payment method in case 036 removed (customer already proposed cash). No KB/database writes, no retrieval/LLM calls, no source refresh. Validation results are not model scores.

Actions describe desired product behavior, not currently supported backend enums; ASK_CLARIFICATION is a target label. No quality claims about live RAG can be made from data validation. Keep existing user changes and branch intact; no commit/push/deploy.
