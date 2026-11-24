# Frontend entry experience

Approved basis: React + TypeScript + Vite, Tailwind layout, Ant Design controls, feature-based frontend alongside backend. User requested implementation of home, login, and registration.

Build a Vietnamese helpdesk entry experience: `/`, `/login`, `/register`. A single brand (Helpdesk), white/slate surfaces, deep teal #115E59, mint #DDF3EA, ink #182C30, muted #637579, white #FFFFFF. Use Be Vietnam Pro with system fallback. Desktop home pairs a left-aligned headline with a clearly labeled sample inbox. Auth uses a quiet split layout, accessible forms, inline validation, server errors, password reveal, submit loading, and a link back home. Mobile stacks content and retains navigation and form access.

Connect existing `/api/v1/auth/login`, `/register`, `/me`, `/refresh`, `/logout` using a Vite `/api` proxy to port 8080. Registration creates CUSTOMER only and sends fullName, email, password and optional phone; confirmation password is client-only. Persist tokens in sessionStorage (tab lifetime), validate restored sessions with `/me`, refresh once on 401, clear failed sessions. Success returns home with an account welcome panel and logout; workspace is out of scope. No social login or password-reset links without supporting flows. No real tickets or fabricated production metrics on home.

Keep components and API code within features, shared Axios client and generated backend types in shared. Use React Router, TanStack Query, React Hook Form and Zod. Build strict TypeScript, validate error paths, real login/registration/logout, desktop/mobile overflow, keyboard access. Do not change backend or deploy externally.
