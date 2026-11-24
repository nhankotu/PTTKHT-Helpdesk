# Two-stage RAG Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement bounded query preprocessing and grounded answering with server-side gates in the existing RAG endpoint.
**Architecture:** AiHelpdeskFacade remains the public module API. A two-stage pipeline validates strict model JSON, assembles public reviewed KB evidence, persists stage claims independently, and returns drafts for agent review. Existing listing/feedback contracts remain compatible.
**Tech Stack:** Java 17, Spring Boot, Jackson, JDBC/JPA, Flyway, MySQL, JUnit/Mockito/Testcontainers.
**Spec:** docs/superpowers/specs/2026-11-24-two-stage-rag-design.md

## Global Constraints
- Each stage <=1 HTTP attempt per logical request, including errors and concurrent retries.
- Mock default; no real external model call during verification.
- Preserve original question and PUBLIC context; reject invented facts/numbers and malformed outputs.
- Missing customer facts -> CLARIFICATION_REQUIRED; missing evidence -> fallback; no automatic sending.
- Existing user changes stay intact; no commits or worktree moves in this session.

## Review Focus
- Reused idempotency keys with changed actor/ticket/body must not expose cached data.
- Revoked or changed KB and indirect injection must not become usable evidence.
- Model 1 fact provenance checks cover subqueries as well as normalized text.
- Process crash/ambiguous network failure must not trigger another provider attempt.
- Correct quote with misleading generated wording still needs agent review; do not claim semantic proof.

### Task 1: Build and integrate the bounded pipeline

**Files:** knowledge/rag/pipeline/* for contracts, gates, evidence, adapters and orchestration; knowledge/service/AiHelpdeskFacade.java integration; knowledge/repository/RagRequestStore.java durable claims; db/migration/V3__two_stage_rag.sql; common/ai/GeminiClient.java bounded calls; ticket/service/TicketPublicContextPort*.java authorization/provenance; KnowledgeDtos.java, AiRunOutcome.java and AiFallbackReason.java; tests under knowledge/rag/pipeline and integration.

**Interfaces:** QueryPreprocessor.prepare(Input) -> JsonNode; GroundedAnswerer.answer(Input, Plan, Evidence) -> JsonNode; Pipeline.execute(Input, topK, requestKey) -> Result; facade persists Result as AiRunDto. Gates reject JSON rather than coerce it. Policy registry locks reviewed KB content/section hashes, not evaluation labels.

- [x] Write gate tests for strict JSON, fabricated numbers/facts, clarification, per-intent evidence and forged/revoked citations; run RED.
- [x] Implement contracts/gates, policy registry and deterministic mock adapters; run GREEN.
- [x] Write orchestration tests for successful answer, invalid preprocessing, missing KB, timeout and duplicate request claims; run RED, implement pipeline and durable JDBC claims, run GREEN.
- [x] Add migration and facade/controller integration, preserve public context authorization and message IDs; test real MySQL claims and API outcomes.
- [x] Run full Maven suite and Python regressions; inspect independent code review and fix important findings with regression tests.
- [x] Update design with actual contracts, usage, limits and test evidence.

Pre-flight: Task 1 consumes only existing facade/port contracts, adapted compatibly; no inter-task interface conflicts.

User steering: also implement provider/model adapters after the pipeline; added StructuredModelAdapter registry, Gemini and OpenAI-compatible adapters, independent role configuration and bounded HTTP transport. No real provider call in verification.

Review: fresh read-only reviewer found eight important gaps plus uncited-context revocation coverage. Regression run `target/review-regressions-red.log` reproduced six failures (unit/refs, free-form IDs, personalized claims, history, missing key, oversized history). Fixes add typed references, extractive policy-only output, per-intent source association, source snapshots, mandatory keys, hash-only oversized fallback, metadata screening, targeted source locks, and full-context fingerprints. Source/intent swap, metadata filtering, targeted SQL and full-context tests cover the remaining paths. Full 90-test run passed after fixes; final run includes the additional metadata positive/negative regression.

Ruling: v1 uses server-rendered whole policy sections rather than model paraphrases, so fee/eligibility logic cannot be invented in generated wording; tradeoff is longer policy drafts, with relevance still checked by agent. Context budget is characters, not a token guarantee. Ongoing/abandoned keys and stale cache return 409; callers must use stable keys and handle conflict. Legacy drafts without source snapshots are redacted. Details are in the spec's implementation section.

Final verification: Maven full suite **91 tests, 0 failures/errors/skips**, `backend/target/two-stage-release-check.log`; Python **21 tests**, frontend build succeeded and **13 tests** passed. Additional focused pipeline regression confirms post-model validation receives both cited and uncited context. OpenAPI/TypeScript types and Postman request updated, JSON collection parsed and required header verified. Independent review's important findings addressed; no reported minor findings deferred. No commit, push, external provider call or runtime production database change was made. Flyway runs were against Testcontainers MySQL; restart the local backend to apply V3–V5 to its own database.
