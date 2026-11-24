# CellphoneS KB collection

User approved collecting two public CellphoneS policy pages, keeping originals, clean Markdown and source metadata, then importing reviewed content into local KB.

- [x] Implement collector with tests for merged tables, scope/footnotes, exclusion of comments, images and source context per chunk.
- [x] Fetch the two approved URLs, save timestamped raw snapshots + hashes; extract only policy body and inspect image/table content.
- [x] Prepare two API-compatible articles. Keep business/source URL/retrieval timestamp and unknown global effective date explicit; record the refund section's 2022-12-12 date within its scope. No schema changes.
- [x] Import via existing local KB APIs, submit/publish reviewed versions, verify chunks match source. Repeat import must not duplicate unchanged articles.
- [x] Document reproducible commands, counts, any gaps and policy conflicts. Keep evaluation separate; question generation is not part of this step.

Verification: 10/10 Python tests pass. 246 source text fragments accounted for in canonical Markdown. Three tables preserve header/row spans and footnotes. Two payment UI images manually inspected and excluded from KB text. Two ambiguous refund sections retained in source Markdown but excluded from published payload. Database/API verification confirms two articles, two published versions, 7 + 11 active chunks, all returned Unicode content exactly matches the approved payload, and second import preserves IDs.

Independent review by review_cellphones_data found three importer blockers; all corrected with RED→GREEN tests: lock complete payload metadata, hash the actual raw file, reject API redirects before credentials are forwarded. No deferred findings. Existing user changes/branch state were left intact.

Constraints: preserve existing user changes (skills moved into backend/.agents, branch behind origin); do not pull, stage, commit or deploy. Public-source policies are dated research snapshots, not live order data or a redistribution license. No external account writes. Credentials/tokens stay in memory/environment, never artifacts or logs.
