# CellphoneS retrieval baseline

User approved step 2: evaluate current retrieval on 50 frozen questions, no Gemini or other LLM calls, no changes to retrieval behavior.

- [x] Validate source/test hashes and compile an offline JVM evaluation harness that invokes the actual private AiHelpdeskFacade retrieval/tokenization/guard methods with the 18 reviewed chunks as repository input.
- [x] Test evidence scoring (AND of groups, OR within group, partial coverage, excluded fallback denominator) before implementation.
- [x] Run top-K=5 and the actual 0.15 rounded-score threshold, question only as query. Report raw retrieval and pre-guard result separately; no inference of answer correctness or full fallback decision.
- [x] Report required group recall, full-evidence coverage and first-hit rank for 40 supported/clarification questions, with breakdowns; report candidate-return risk for 10 negatives separately.
- [x] Expose ties at cutoff and compare reversed repository order. Production JPQL does not define order; do not present one offline tie ordering as exact database behavior.
- [x] Export machine-readable results and a readable report, independent metric review, rerun checks and explain actual findings. Preserve source/KB/evaluation labels and existing user changes; no SQL/API writes or commits.

Outcome: complete evidence 35/40, any evidence 36/40, evidence groups 43/48, MRR 0.7625. Nine of ten negatives have post-guard candidates; one literal injection marker is blocked. 33 cutoff ties change membership when pool reversed; complete-evidence total stays 35/40 (not a proof of best/worst order). Misses: 021,022,028 (no accents),030 (required chunk ranks 6),032 (short ship query). Actual production methods retained unchanged.

Verification: Java scoring tests observed RED then GREEN; 9 targeted Java tests pass, including existing RAG invariants. Three Python report tests RED→GREEN; full scripts suite 21 pass. Independent review by review_retrieval_baseline confirmed Java method use, no ground-truth leakage, hash alignment, aggregate counts, reporter denominators/MRR and careful limits; no blockers or deferred findings.
