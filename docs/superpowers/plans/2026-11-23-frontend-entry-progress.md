# Frontend entry progress

Plan: 2026-11-23-frontend-entry.md

- Task 1 complete: scaffold and dependencies; auth tests observed RED on missing modules, then 11/11 passed after implementation.
- Task 2 complete: generated OpenAPI DTO types, sessionStorage fallback, Axios refresh coalescing, stale-refresh logout protection, auth context.
- Task 3 complete: Vietnamese responsive home, interactive example channels, login/register forms, labeled decorative icons, keyboard tab switching, 404.
- Task 4 complete: build passed, 13 unit tests passed, 5 E2E scenarios passed (real MySQL/backend plus mocked error/race paths). Home/login/register visually inspected on desktop/mobile; no horizontal overflow at 320/390/768/1440 widths.
- Compatibility: scaffold TypeScript 7 was incompatible with openapi-typescript peer range ^5.x; use TypeScript ^5.9.3 instead, strict mode retained.
- Local preview: use localhost:5173 to match existing backend CORS; 127.0.0.1 origin verified 403, localhost verified 200. No backend changes.
- Final independent review: completed by review_frontend_entry; three important findings fixed with tests observed RED then GREEN.
- Final: fixed missing logout bearer and logout replay — auth.api.test.ts and client.test.ts, RED→GREEN.
- Final: fixed offline queued authentication — browser offline E2E, RED→GREEN; auth queries/mutations use networkMode always.
- Final: fixed old login response replacing newer identity — cross-form navigation E2E, RED→GREEN; requests canceled on form unmount and signals checked before session acceptance.
- Final: minor (deferred): manually corrupted/whitespace token values may remain in sessionStorage; malformed JSON renders anonymously, invalid-token restore offers Xóa phiên. No valid backend-issued token is affected. Documented in frontend README.
- Integration: leave local changes on nhanth for the user to review; no commit, push, merge or external deployment requested.
