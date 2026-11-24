# Frontend entry implementation plan

> Execute directly in this session using executing-plans. User already approved architecture and requested code.

**Goal:** Deliver working Vietnamese home, login, registration pages connected to the existing backend.

**Architecture:** Feature-based React SPA in frontend; shared API client with tab-scoped session; public routes and account welcome on home.

**Tech Stack:** React, TypeScript strict, Vite, Tailwind, Ant Design, React Router, TanStack Query, Axios, React Hook Form, Zod, Vitest, Playwright.

**Spec:** ../specs/2026-11-23-frontend-entry.md

## Global constraints
- Keep backend unchanged and work on nhanth.
- Register CUSTOMER only; backend password length 8–100 and full name max 150.
- Session tokens never in logs, screenshot artifacts, or committed fixtures.
- Home sample conversation must be labeled as demonstration.

## Review focus
- Whitespace/invalid email, mismatched password, and duplicate account: actionable errors.
- Backend offline: error and retry without losing input.
- Expired/invalid stored session: refresh once or clear, no retry loop.
- Repeated submit: one pending action; mobile and keyboard can reach fields.
- Malformed stored state cannot prevent page rendering; logout clears local session.

## Tasks
- [x] Scaffold frontend, install dependencies; add auth schema tests for invalid data, limits and password confirmation and observe RED before implementing schemas.
- [x] Add shared Axios client, generated OpenAPI types, auth API/session context; verify auth session/error tests.
- [x] Implement home, responsive header, preview conversation, auth layout and login/register forms with consistent design tokens.
- [x] Build and test, run browser checks (desktop/mobile, validation, invalid login, real register/login/logout, reload), review resulting diff and document run instructions.
