# Building the Expense Splitter with AI — Setup + Prompt Sequence

Acting as the senior dev on this: the trap with "prompt an AI to build my app" is people paste one giant prompt and get a pile of code nobody — including them — understands or can defend in an interview. The sequence below builds it the way a competent team would: scaffold → data layer → core algorithm (tested in isolation) → security/auth → features → frontend → hardening → deploy. Each prompt assumes the previous ones are done and in the repo, and each one tells the AI to explain what it did, not just dump code.

Feed the AI `expense-splitter-design.md` (attach it or paste it) at the *start* of the session and reference it in prompts — don't re-paste the whole doc every time.

---

## Part A — Set Up Before You Prompt Anything

Do this first. An AI tool can't do these for you (or shouldn't — you want to understand your own project setup).

### 1. Accounts / external services
- **GitHub** — create the empty repo now (`expense-splitter`), so every AI-generated commit has a real history.
- **Email delivery** — pick one:
  - **Mailtrap** (recommended for dev) — free, catches emails in a fake inbox, zero risk of spamming real addresses while testing.
  - **SendGrid** or **Resend** — free tier, use for the "production" deployed demo so verification emails actually arrive.
- **FX API** — no signup needed if you use Frankfurter (per the design doc); if you switch to exchangerate.host, grab a free key.
- **Deployment targets** — create free accounts now so env vars are ready later: Railway or Render (backend + Postgres + Redis), Vercel or Netlify (frontend).

### 2. Local tooling
Install and verify versions before prompting:
```bash
java -version      # need 21+
mvn -version        # 3.9+
node -v              # 20+
npm -v
docker -v
docker compose version
```

### 3. Repo skeleton (do this manually, not via AI)
```bash
mkdir expense-splitter && cd expense-splitter
git init
mkdir backend frontend docs
echo "node_modules/\ntarget/\n.env\n*.log\n.idea/\n.vscode/" > .gitignore
git add .gitignore && git commit -m "init"
```
Copy `expense-splitter-design.md` into `docs/`.

### 4. Secrets file (never commit this)
Create `backend/.env.example` (commit this) and `backend/.env` (don't commit):
```
DB_URL=jdbc:postgresql://localhost:5432/expense_splitter
DB_USER=postgres
DB_PASSWORD=changeme
JWT_SECRET=
JWT_REFRESH_SECRET=
REDIS_HOST=localhost
REDIS_PORT=6379
MAIL_HOST=sandbox.smtp.mailtrap.io
MAIL_PORT=2525
MAIL_USERNAME=
MAIL_PASSWORD=
FX_API_BASE_URL=https://api.frankfurter.dev
APP_BASE_URL=http://localhost:5173
```
Generate real JWT secrets yourself (don't let the AI invent placeholder secrets and leave them in code):
```bash
openssl rand -base64 64
```

### 5. docker-compose for local Postgres + Redis
Write this yourself (it's short and you should know exactly what's running):
```yaml
version: "3.9"
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: expense_splitter
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: changeme
    ports: ["5432:5432"]
    volumes: ["pgdata:/var/lib/postgresql/data"]
  redis:
    image: redis:7
    ports: ["6379:6379"]
volumes:
  pgdata:
```
Run it: `docker compose up -d`, confirm both containers are healthy before prompting the AI to write anything that connects to them.

Now you're ready to prompt.

---

## Part B — Prompt Sequence

Use a fresh, focused prompt per step. Review the diff before moving to the next prompt — don't chain 10 prompts unread.

### Prompt 1 — Backend scaffold
```
I'm building the project described in the attached expense-splitter-design.md.
Set up the Spring Boot backend skeleton only — no business logic yet.

- Java 21, Spring Boot 3.x, Maven
- Dependencies: spring-web, spring-data-jpa, postgresql driver, flyway-core,
  spring-boot-starter-validation, spring-boot-starter-security, spring-boot-starter-mail,
  spring-data-redis, jjwt (or spring's JWT support), lombok, springdoc-openapi
- Package structure exactly as in the design doc's "Project Structure" section
- application.yml should read all config from environment variables (reference
  backend/.env.example for the variable names) — no hardcoded secrets or URLs anywhere
- Add a docker-compose.yml reference / README note on how to run against the existing
  local postgres+redis containers
- Add a health check endpoint GET /api/health

Explain any dependency choices that differ from what I listed, and flag anything
in the design doc that seems architecturally off before implementing it.
```

### Prompt 2 — Database schema + entities
```
Implement the Flyway migration (V1__init.sql) using the exact schema from
expense-splitter-design.md section 3, plus these additions:

- users table: add `email_verified BOOLEAN DEFAULT false`,
  `verification_token VARCHAR(255)`, `verification_token_expires_at TIMESTAMPTZ`
- a `refresh_tokens` table (id, user_id, token_hash, expires_at, revoked BOOLEAN,
  created_at) — refresh tokens must be stored hashed, never in plaintext
- expenses table: ensure `group_id` is nullable (NULL for personal/individual expenses),
  and includes `category VARCHAR(50) NOT NULL DEFAULT 'OTHER'` with an `ExpenseCategory`
  enum (FOOD, TRANSPORT, HOUSING, ENTERTAINMENT, SHOPPING, HEALTH, TRAVEL, UTILITIES, OTHER)
  and `split_type` supports `PERSONAL` alongside EQUAL, EXACT, PERCENTAGE, SHARES.

Then create the corresponding JPA entities matching the migration exactly
(field types, nullability, constraints). Use Lombok minimally — prefer explicit
getters/setters over @Data on entities to avoid accidental toString/equals issues
with lazy-loaded associations. Don't build repositories or services yet.

Ask me before making any schema decision not already specified in the design doc.
```

### Prompt 3 — Auth: registration + email verification (the part you flagged)
```
Implement registration and email verification. Security requirements — do not
skip any of these:

1. Passwords hashed with BCrypt (strength 12), never logged, never returned in
   any response DTO.
2. On registration: create the user with email_verified=false, generate a
   cryptographically random verification token (UUID or SecureRandom, not
   sequential/guessable), set a 24-hour expiry, store only if you must — prefer
   storing a hash of the token, not the raw token, same as refresh tokens.
3. Send a verification email via spring-boot-starter-mail using the MAIL_* env
   vars, with a link to {APP_BASE_URL}/verify?token=... — use a real HTML email
   template, not a plaintext one-liner.
4. GET /api/auth/verify?token=... : validate token exists, not expired, not
   already used. On success mark email_verified=true and invalidate the token
   (delete it or mark used — don't let it be replayed).
5. Login must reject unverified accounts with a clear 403 and a distinct error
   code (e.g. EMAIL_NOT_VERIFIED) so the frontend can show "resend verification"
   instead of a generic login error.
6. Add POST /api/auth/resend-verification — rate-limited (see prompt 6) so it
   can't be used to spam an inbox or enumerate accounts. Always return the same
   generic success response whether or not the email exists, to avoid user
   enumeration.
7. Write unit tests: expired token rejected, reused token rejected, already-
   verified account handled gracefully, unverified login blocked.

Show me the email template and explain your token-expiry and hashing choices.
```

### Prompt 4 — Auth: login, JWT, refresh rotation
```
Implement login and JWT issuing.

- Access token: short-lived (15 min), signed with JWT_SECRET, includes user id
  and email only — no sensitive data in the payload since JWTs are decodable,
  not encrypted.
- Refresh token: long-lived (7-14 days), opaque random value, stored HASHED in
  the refresh_tokens table (per prompt 2's schema), returned to the client as an
  httpOnly, Secure, SameSite=Strict cookie — not in the JSON body and not stored
  in localStorage (explain why if you'd normally do it differently).
- POST /api/auth/refresh: validate the refresh token against its hash, check
  not expired/not revoked, issue a new access token, and ROTATE the refresh
  token (issue a new one, revoke the old) to limit replay risk if one leaks.
- POST /api/auth/logout: revoke the refresh token server-side and clear the
  cookie.
- Global exception handler that never leaks stack traces or internal error
  detail to the client in production profile.

Write tests for: expired access token rejected, revoked refresh token rejected,
refresh token reuse after rotation is detected and treated as a breach (revoke
the whole token family).
```

### Prompt 5 — Authorization / access control
```
Add method-level authorization so the security model has no holes:

- Every group-scoped endpoint (expenses, balances, settlements, activity,
  export) must verify the authenticated user is a member of that group via
  @PreAuthorize or a custom security expression — not just "logged in."
- Users can only edit/delete expenses they created OR are a group admin for
  (add an is_admin flag or role to group_members if not already there — check
  the design doc schema and extend it, tell me what you changed).
- Add integration tests proving: a logged-in user from Group A gets 403 (not
  404 — don't leak existence) when hitting Group B's endpoints.

List every endpoint from the design doc's API surface and confirm which
authorization rule applies to each, in a table, before implementing.
```

### Prompt 6 — Rate limiting & input hardening
```
Add these cross-cutting protections:

- Rate limit /api/auth/login, /api/auth/register, /api/auth/resend-verification
  per-IP (e.g. bucket4j with Redis backing, since Redis is already in the stack)
- Bean Validation (@Valid + constraints) on every request DTO — amounts must be
  positive, currency must be a valid ISO code, emails validated, string lengths
  bounded to prevent abuse
- Explicit CORS config allowing only APP_BASE_URL, not "*"
- Security headers: Content-Security-Policy, X-Content-Type-Options,
  X-Frame-Options via Spring Security's header support
- Confirm CSRF handling is appropriate given we're using httpOnly cookies for
  refresh tokens (explain the tradeoff and what you're enabling/disabling)

Summarize the full security checklist we've now covered vs. anything still
open, in a short table.
```

### Prompt 7 — Split calculator + expense CRUD & personal expenses
```
Implement expense creation/read/update/delete plus the SplitCalculator
strategy pattern from the design doc (EQUAL, EXACT, PERCENTAGE, SHARES, PERSONAL).

- All money math uses BigDecimal, rounding mode HALF_UP, and split amounts
  must sum exactly to the expense total — if rounding creates a 1-cent
  discrepancy, assign the remainder to the first payer deterministically
  (write a test for this specific edge case).
- Reject a create/update request where percentages don't sum to 100 or shares
  don't sum sensibly.
- Only group members can be included in a group split.
- Personal expenses: Implement PersonalExpenseService and endpoints for individual
  expenses (POST /api/expenses/personal, GET /api/expenses/personal with category
  and date range filters, GET /api/expenses/personal/analytics for category
  breakdown & monthly summary, GET /api/categories).
- Ensure personal expenses (where group_id IS NULL) are owned strictly by the
  authenticated user and are excluded from group debt & balance calculations.

Unit test each split strategy independently, personal expense isolation, plus
the rounding-remainder edge case above.
```

### Prompt 8 — Balance calculation + Debt Simplifier
```
Implement BalanceService (net balance per user per group) and then the
DebtSimplifier exactly as specified in expense-splitter-design.md section 4 —
use the code there as the starting point, but push further on the test suite:

- Randomized property test: generate N random balances that sum to zero,
  run simplify(), assert (a) every resulting settlement is positive, (b) total
  settled amount equals total original debt, (c) applying all settlements
  brings every user's balance to exactly zero, (d) transaction count never
  exceeds n-1.
- Zero-debt group produces zero settlements.
- Single creditor / many debtors and reverse.

This is the algorithm I want to be able to explain in interviews, so don't
just implement it — add a short comment block above the class explaining the
greedy strategy and its complexity, in language I could repeat out loud.
```

### Prompt 9 — Settlements, recurring expenses, activity log
```
Implement (per our decision to build recurring expenses + a mock settlement
ledger as the two stand-out features, skipping OCR):

1. GET /api/groups/{id}/settlements/suggested — runs DebtSimplifier on current
   balances, returns suggested transactions, doesn't persist anything.
2. POST /api/groups/{id}/settlements — records an actual settlement (manual or
   from a suggestion), inserts into settlements table, and this MUST reduce
   the relevant users' computed balances going forward — walk me through how
   the balance query accounts for settlements vs. raw expense shares.
3. Recurring expenses: entity + a Spring @Scheduled job (run daily, check
   next_run_at <= now) that creates a real expense row and advances
   next_run_at. Make it idempotent — running the job twice in the same day
   must not double-create expenses.
4. Audit log: every expense create/edit/delete and every settlement writes an
   audit_log row. Add GET /api/groups/{id}/activity, paginated, newest first.

Test the recurring job's idempotency and the settlement-affects-balance logic
specifically.
```

### Prompt 10 — FX + Redis caching
```
Implement FxRateClient (calls Frankfurter per FX_API_BASE_URL) and
FxRateService with Redis caching as described in the design doc section 6.

- Cache miss → fetch → cache with 24h TTL → return.
- If the external API is down and there's no cached value, fail the specific
  request gracefully (don't crash the whole expense creation flow) — return a
  clear error the frontend can show, and let the user retry or enter the
  amount in the group's default currency instead.
- Confirm: are we storing converted amounts at write time as the design doc
  specifies? Show me exactly where that conversion happens in the expense
  creation flow.
```

### Prompt 11 — Frontend scaffold
```
Set up the React + TypeScript + Vite frontend per the design doc's structure.

- Tailwind CSS + shadcn/ui, TanStack Query for server state, Zustand for local
  UI state, react-hook-form + zod for form validation matching backend
  validation rules
- Typed API client (generate types from the backend's OpenAPI spec if
  reasonable, or hand-write matching types) — no `any` on API response shapes
- Auth flow: login/register pages, a "check your email" screen after
  registration, a /verify route that calls the verify endpoint and shows
  success/error, protected route wrapper that redirects unauthenticated users
- Access token held in memory (React state/context), NOT localStorage — explain
  why given we're using httpOnly cookies for the refresh token
- Mobile-first responsive layout from the start, not retrofitted — every page
  must work at 375px width before we add desktop breakpoints

Show me the responsive breakpoint strategy before building every page.
```

### Prompt 12 — Core UI: groups, expenses, balances dashboard & categories
```
Build the group list, group detail (expense list + add-expense form
supporting all 4 split types plus category tags), and balances dashboard.

- Balances dashboard: table of net positions plus a visual debt graph (use
  Recharts, or a simple SVG force layout if Recharts doesn't fit) showing
  who-owes-who, and a "Suggested settlements" panel calling the
  /settlements/suggested endpoint
- Category UI: Add category picker and CategoryBadge components (with distinct icons/colors)
  for expenses.
- Every list/table must have a sensible empty state and loading skeleton, not
  a blank screen or spinner-forever
- Confirm this passes a basic responsive check: single-column stacked layout
  under 640px, no horizontal scroll, tap targets at least 44px on mobile

Apply the frontend-design skill/best-practices for this environment — I want
this to look like a real product, not a bootstrap-default CRUD app. Ask me
about visual direction (color palette, typography, mood) before generating
full pages if it's not obvious from context.
```

### Prompt 13 — Personal expenses tracker, activity feed, recurring UI, settlements
```
Build the remaining screens:
1. Personal Expenses Tracker: dedicated personal expense ledger with category
   filtering, date picker, quick add personal expense modal, and spending analytics
   dashboard (Recharts donut chart for category breakdown and monthly spending trends).
2. Activity/audit feed (paginated) for groups.
3. Recurring expense setup form.
4. "Record settlement" flow that lets a user confirm a suggested settlement or
   manually log one. Wire up CSV/PDF export buttons on the group page.

Reuse existing components — don't duplicate table/list rendering logic across
these screens.
```

### Prompt 14 — Security review pass (do this even though earlier prompts included security)
```
Do a dedicated security review of the whole codebase so far, backend and
frontend. Go through this checklist explicitly and report pass/fail with the
specific file/line for anything that fails, don't just say "looks fine":

- No secrets, API keys, or credentials committed anywhere (check .env is
  gitignored and was never committed)
- All SQL access goes through JPA/parameterized queries, no string-concatenated
  queries anywhere
- Every user-supplied string rendered in the frontend goes through React's
  default escaping — no dangerouslySetInnerHTML with unsanitized content
- File/amount/currency validation can't be bypassed by hitting the API
  directly (not just blocked in the UI)
- JWT secret is loaded from env, sufficiently long/random, never has a
  fallback default value in code
- Password reset/verification tokens are single-use and expire
- Rate limiting actually applies (write a quick test hitting login 10x fast)
- CORS doesn't allow arbitrary origins
- Dependency check: list any backend or frontend dependencies with known CVEs
  (reason about versions, or tell me to run `npm audit` / OWASP dependency
  check if you can't verify directly)

Fix anything that fails before we move to deployment.
```

### Prompt 15 — Dockerize + deploy config
```
Add a production Dockerfile for the backend (multi-stage build, don't ship the
JDK in the final image — use a JRE base) and confirm the frontend build output
is a static bundle deployable to Vercel/Netlify as-is.

- Backend: read all config from env vars in production, application-prod.yml
  should have no secrets or localhost references
- Add a docker-compose.prod.yml note or Railway/Render service config
  reflecting the env vars from backend/.env.example
- CORS/APP_BASE_URL must be configurable per environment, not hardcoded to
  localhost
- Write a deployment section in the README: exact steps to deploy backend +
  Postgres + Redis to Railway/Render and frontend to Vercel, including which
  env vars to set where
```

### Prompt 16 — README + docs polish
```
Write the final README: project description, architecture diagram (mermaid is
fine), tech stack table, how to run locally (docker compose + both dev
servers), how the debt-simplification algorithm works (link to
docs/algorithm-explainer.md), screenshots section (placeholder for me to add
real ones), and live demo link placeholder. Keep it scannable — this is what a
recruiter skims for 30 seconds.
```

---

## Notes on how to use this well

- **Review every diff.** If a prompt's output touches auth, tokens, or money math, read it line by line before prompt N+1 — mistakes compound.
- **Run the tests after every prompt that adds them.** Don't let three prompts pass with a red test suite.
- **If the AI's output disagrees with the design doc**, ask it to explain why before accepting the deviation — sometimes it's a legitimate improvement, sometimes it's drift.
- **Keep prompts 3–6 (auth/security) as your own checklist** even outside this workflow — that's the sequence that prevents the classic "looks done, isn't secure" portfolio project.
