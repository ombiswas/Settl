# Smart Expense-Splitting App — Full Design Doc

**Working name:** Settl (placeholder — swap for whatever you like)

## 1. Tech Stack

| Layer | Choice | Why |
|---|---|---|
| Backend | Java 21 + Spring Boot 3.x | Matches your Trace project — consistent story for interviews |
| API style | REST (OpenAPI-documented) | Standard, easy to demo with Swagger UI |
| Database | PostgreSQL 16 | Relational integrity matters here (money, foreign keys) |
| Cache | Redis 7 | FX rate caching, and later session/rate-limit reuse |
| Auth | Spring Security + JWT (access + refresh) | Same pattern as Trace, reusable knowledge |
| Frontend | React 18 + TypeScript + Vite | Fast dev loop, type safety for money math |
| Frontend state | TanStack Query + Zustand | Query for server cache, Zustand for lightweight UI state |
| Styling | Tailwind CSS + shadcn/ui | Fast, professional-looking UI without custom CSS overhead |
| Charts | Recharts | Balances dashboard, net position visualization |
| ORM | Spring Data JPA + Hibernate | Standard; use explicit DTOs, don't leak entities |
| Migrations | Flyway | Version-controlled schema, looks good in a repo |
| Testing | JUnit 5 + Mockito (backend), Vitest + RTL (frontend) | Debt-simplification algorithm needs heavy unit testing |
| Build | Maven | Same as Trace |
| Containerization | Docker + docker-compose | Postgres + Redis + backend + frontend, one command up |
| Deployment | Backend: Render/Railway/Fly.io. Frontend: Vercel/Netlify | Free tiers, easy demo links |
| Docs | OpenAPI/Swagger + README with architecture diagram | Recruiters skim READMEs — make it count |

Optional stand-outs (pick 2, as you noted):
- **Receipt OCR**: Tesseract via `tess4j` (Java binding) — keeps everything in your stack, no extra cloud dependency/cost
- **PDF/CSV export**: Apache PDFBox or OpenPDF for PDF, plain CSV writer for CSV
- **Recurring expenses**: Spring `@Scheduled` job, simplest to build and explain
- Mock settlement ledger is nearly free once `settlements` table exists — I'd include it regardless of which two you pick

## 2. Project Structure

```
expense-splitter/
├── backend/
│   ├── src/main/java/com/yourname/expensesplitter/
│   │   ├── config/
│   │   │   ├── SecurityConfig.java
│   │   │   ├── RedisConfig.java
│   │   │   └── OpenApiConfig.java
│   │   ├── auth/
│   │   │   ├── AuthController.java
│   │   │   ├── JwtService.java
│   │   │   └── JwtAuthFilter.java
│   │   ├── user/
│   │   │   ├── User.java
│   │   │   ├── UserRepository.java
│   │   │   └── UserService.java
│   │   ├── group/
│   │   │   ├── Group.java
│   │   │   ├── GroupMember.java
│   │   │   ├── GroupController.java
│   │   │   ├── GroupService.java
│   │   │   └── GroupRepository.java
│   │   ├── expense/
│   │   │   ├── Expense.java
│   │   │   ├── ExpenseShare.java
│   │   │   ├── SplitType.java          (enum: EQUAL, EXACT, PERCENTAGE, SHARES, PERSONAL)
│   │   │   ├── ExpenseCategory.java    (enum: FOOD, TRANSPORT, HOUSING, ENTERTAINMENT, SHOPPING, HEALTH, TRAVEL, UTILITIES, OTHER)
│   │   │   ├── ExpenseController.java
│   │   │   ├── PersonalExpenseController.java
│   │   │   ├── ExpenseService.java
│   │   │   ├── PersonalExpenseService.java
│   │   │   ├── SplitCalculator.java     (strategy pattern per split type)
│   │   │   └── ExpenseRepository.java
│   │   ├── balance/
│   │   │   ├── Balance.java             (DTO, not entity — computed)
│   │   │   ├── BalanceService.java
│   │   │   └── BalanceController.java
│   │   ├── settlement/
│   │   │   ├── Settlement.java
│   │   │   ├── DebtSimplifier.java      ← the centerpiece algorithm
│   │   │   ├── DebtSimplifierTest.java
│   │   │   ├── SettlementController.java
│   │   │   └── SettlementService.java
│   │   ├── recurring/
│   │   │   ├── RecurringExpense.java
│   │   │   └── RecurringExpenseScheduler.java
│   │   ├── fx/
│   │   │   ├── FxRateClient.java        (external API call)
│   │   │   ├── FxRateService.java       (Redis-cached wrapper)
│   │   │   └── FxRateController.java
│   │   ├── ocr/
│   │   │   ├── ReceiptOcrService.java
│   │   │   └── ReceiptController.java
│   │   ├── audit/
│   │   │   ├── AuditLogEntry.java
│   │   │   └── AuditLogService.java
│   │   ├── export/
│   │   │   ├── PdfExportService.java
│   │   │   └── CsvExportService.java
│   │   └── common/
│   │       ├── ApiException.java
│   │       ├── GlobalExceptionHandler.java
│   │       └── Money.java               (wraps BigDecimal + currency, avoid double)
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   ├── application-dev.yml
│   │   ├── application-prod.yml
│   │   └── db/migration/                (Flyway: V1__init.sql, V2__..., etc.)
│   ├── src/test/java/...                (mirrors main structure)
│   └── pom.xml
│
├── frontend/
│   ├── src/
│   │   ├── api/                         (typed API client functions)
│   │   ├── components/
│   │   │   ├── groups/
│   │   │   ├── expenses/
│   │   │   ├── personal/                (personal expenses, category analytics charts)
│   │   │   ├── balances/
│   │   │   │   └── BalanceGraph.tsx     (visualize debt graph)
│   │   │   ├── settlements/
│   │   │   └── ui/                      (shadcn primitives)
│   │   ├── hooks/                       (useGroups, useBalances, etc.)
│   │   ├── pages/
│   │   ├── store/                       (zustand slices)
│   │   ├── types/
│   │   └── App.tsx
│   ├── index.html
│   └── package.json
│
├── docker-compose.yml
├── docs/
│   ├── architecture.png
│   ├── er-diagram.png
│   └── algorithm-explainer.md           (write this — great interview leave-behind)
└── README.md
```

## 3. Database Schema (detailed)

```sql
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(150) NOT NULL,
    default_currency CHAR(3) NOT NULL DEFAULT 'USD',
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE group_members (
    group_id UUID REFERENCES groups(id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    joined_at TIMESTAMPTZ DEFAULT now(),
    PRIMARY KEY (group_id, user_id)
);

CREATE TABLE expenses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID REFERENCES groups(id) ON DELETE CASCADE, -- NULL for personal/individual expenses
    paid_by UUID REFERENCES users(id) NOT NULL,
    description VARCHAR(255) NOT NULL,
    amount NUMERIC(12,2) NOT NULL,
    currency CHAR(3) NOT NULL,
    category VARCHAR(50) NOT NULL DEFAULT 'OTHER',        -- FOOD, TRANSPORT, HOUSING, ENTERTAINMENT, SHOPPING, HEALTH, TRAVEL, UTILITIES, OTHER
    split_type VARCHAR(20) NOT NULL,                      -- EQUAL, EXACT, PERCENTAGE, SHARES, PERSONAL
    receipt_url TEXT,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE expense_shares (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    expense_id UUID REFERENCES expenses(id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(id),
    amount_owed NUMERIC(12,2) NOT NULL,   -- always resolved to expense's currency
    share_value NUMERIC(8,4),             -- raw % or share count, for audit/edit
    UNIQUE(expense_id, user_id)
);

CREATE TABLE settlements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID REFERENCES groups(id) ON DELETE CASCADE,
    from_user UUID REFERENCES users(id),
    to_user UUID REFERENCES users(id),
    amount NUMERIC(12,2) NOT NULL,
    currency CHAR(3) NOT NULL,
    settled_at TIMESTAMPTZ DEFAULT now(),
    is_simplified BOOLEAN DEFAULT true    -- true if system-generated vs manual
);

CREATE TABLE recurring_expenses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID REFERENCES groups(id) ON DELETE CASCADE,
    template_description VARCHAR(255),
    amount NUMERIC(12,2) NOT NULL,
    currency CHAR(3) NOT NULL,
    split_type VARCHAR(20) NOT NULL,
    paid_by UUID REFERENCES users(id),
    frequency VARCHAR(20) NOT NULL,       -- MONTHLY, WEEKLY
    next_run_at TIMESTAMPTZ NOT NULL,
    active BOOLEAN DEFAULT true
);

CREATE TABLE audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID REFERENCES groups(id) ON DELETE CASCADE,
    actor_id UUID REFERENCES users(id),
    action VARCHAR(50) NOT NULL,          -- EXPENSE_ADDED, SETTLEMENT_RECORDED, etc.
    details JSONB,
    created_at TIMESTAMPTZ DEFAULT now()
);
```

Notes:
- `NUMERIC(12,2)` not `DOUBLE` / `float` — money bugs are the #1 thing that will make this look amateurish in review. Wrap in a `Money` value object on the Java side (BigDecimal + currency, no raw arithmetic scattered around).
- `expense_shares.amount_owed` is always stored converted to the expense's currency at creation time — don't recompute historical shares if FX rates move later.
- Personal/Individual expenses have `group_id = NULL` and `split_type = PERSONAL`. They are isolated from group balances and the debt simplifier, but participate in personal spending analytics and category breakdowns.

## 4. The Debt-Simplification Algorithm (the centerpiece)

This is what you want to be able to whiteboard in an interview, so build it as a pure, framework-free, independently testable class.

**Approach — greedy max-heap matching:**

1. Compute each user's **net balance** in the group (positive = owed money, negative = owes money), by summing `expense_shares` against what they paid.
2. Push all creditors (net > 0) into a max-heap, all debtors (net < 0) into a max-heap (by absolute value).
3. Loop: pop the largest creditor and largest debtor. Settle `min(creditor_amount, debtor_amount)` between them — record a `Settlement`. Push back whichever side has remaining balance. Continue until both heaps are empty.

```java
public class DebtSimplifier {

    public List<SettlementDTO> simplify(Map<UUID, BigDecimal> netBalances) {
        PriorityQueue<Balance> creditors = new PriorityQueue<>(
            Comparator.comparing(Balance::amount).reversed());
        PriorityQueue<Balance> debtors = new PriorityQueue<>(
            Comparator.comparing(Balance::amount).reversed());

        netBalances.forEach((userId, amount) -> {
            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                creditors.add(new Balance(userId, amount));
            } else if (amount.compareTo(BigDecimal.ZERO) < 0) {
                debtors.add(new Balance(userId, amount.abs()));
            }
        });

        List<SettlementDTO> settlements = new ArrayList<>();

        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            Balance creditor = creditors.poll();
            Balance debtor = debtors.poll();

            BigDecimal settleAmount = creditor.amount().min(debtor.amount());
            settlements.add(new SettlementDTO(debtor.userId(), creditor.userId(), settleAmount));

            BigDecimal creditorRemainder = creditor.amount().subtract(settleAmount);
            BigDecimal debtorRemainder = debtor.amount().subtract(settleAmount);

            if (creditorRemainder.compareTo(BigDecimal.ZERO) > 0) {
                creditors.add(new Balance(creditor.userId(), creditorRemainder));
            }
            if (debtorRemainder.compareTo(BigDecimal.ZERO) > 0) {
                debtors.add(new Balance(debtor.userId(), debtorRemainder));
            }
        }
        return settlements;
    }
}
```

**Complexity to cite in an interview:** with `n` participants, this produces at most `n - 1` transactions (provably optimal minimum is `n - c` where `c` is the number of "zero-sum disconnected components," but `n-1` is a safe, correct upper bound to claim), versus up to `n(n-1)/2` pairwise debts in the naive case. Runtime is `O(n log n)` from the heap operations.

**Test cases to write (this is what makes it "carefully unit tested," per your build order):**
- Simple 3-person cycle (A owes B, B owes C, C owes A) → should collapse to fewer transactions
- Exact balance chains that net to zero for everyone → 0 transactions
- Large group (10+ people) with random balances → verify total settled amount equals total debt, and every person's post-settlement balance is exactly 0
- Floating-point/rounding edge cases (use BigDecimal throughout, assert no penny drift)
- Single creditor, many small debtors and vice versa

Put a short `docs/algorithm-explainer.md` with a before/after diagram (messy web → simplified) — genuinely useful as a portfolio artifact you can screenshot for LinkedIn or attach to a resume.

## 5. API Surface (high level)

```
POST   /api/auth/register
POST   /api/auth/login
POST   /api/auth/refresh

GET    /api/groups
POST   /api/groups
GET    /api/groups/{id}
POST   /api/groups/{id}/members

POST   /api/groups/{id}/expenses
GET    /api/groups/{id}/expenses
PUT    /api/expenses/{id}
DELETE /api/expenses/{id}
POST   /api/expenses/{id}/receipt        (multipart upload → OCR)

GET    /api/expenses/personal            (filter by category, date range, pagination)
POST   /api/expenses/personal            (log personal expense with category)
GET    /api/expenses/personal/analytics  (category spending breakdown, totals, monthly stats)
GET    /api/categories                   (list supported expense categories)

GET    /api/groups/{id}/balances         (raw pairwise balances)
GET    /api/groups/{id}/settlements/suggested   (runs DebtSimplifier)
POST   /api/groups/{id}/settlements      (record an actual settlement)

GET    /api/groups/{id}/activity         (audit log, paginated)
GET    /api/groups/{id}/export?format=pdf|csv

GET    /api/fx/rates?base=USD            (Redis-cached)
```

## 6. FX Integration + Redis Caching

- Provider: [exchangerate.host](https://exchangerate.host) or [Frankfurter](https://frankfurter.dev) — both free, no key required for Frankfurter, good for a portfolio project.
- Cache key: `fx:{base}:{date}` → JSON blob of rates, TTL 12–24h (rates don't need to be live-live for a splitting app).
- `FxRateService` checks Redis first, falls back to the HTTP client on miss, repopulates cache.
- Store the *converted* amount at expense-creation time in `expense_shares` — don't re-derive historical splits from live rates later, or old expenses will silently change value.

## 7. Build Order (yours, lightly annotated)

1. **Groups + expenses + equal split** — MVP CRUD, get Flyway/JPA/React wiring working end-to-end
2. **Uneven splits & individual expenses** (percentage/shares, personal expenses, categories) — build `SplitCalculator` as a strategy interface (`EqualSplitStrategy`, `PercentageSplitStrategy`, `SharesSplitStrategy`, `ExactSplitStrategy`, `PersonalExpenseStrategy`) and category classification
3. **Balance calculation** — pure aggregation query/service, unit test heavily
4. **Debt simplification algorithm** — the centerpiece; build and test in isolation before wiring to the API
5. **Auth (JWT)** — lock down group membership, `@PreAuthorize` checks, isolate personal expense access to authenticated owner
6. **FX + Redis** — add multi-currency, cache
7. **React dashboard** — balances table + a force-directed or Sankey-style graph of the debt network (Recharts or a small D3 snippet), plus personal expense tracker & category spending charts
8. **Recurring expenses + export** + settlement ledger
9. **Deploy + document** — docker-compose for local dev, deploy backend/frontend separately, write the README with an architecture diagram and a link to `algorithm-explainer.md`

## 8. Interview Talking Points This Gives You

- "Modeled expense splitting with a strategy pattern so adding a new split type doesn't touch existing code."
- "Engineered a unified expense model supporting both group splits and personal categorized spending with analytics, ensuring zero contamination of group debt graphs."
- "Implemented a greedy debt-simplification algorithm that reduces settlement transactions from O(n²) to O(n), unit tested against randomized balance sets to verify correctness (zero post-settlement balances, conserved total)."
- "Used BigDecimal/NUMERIC throughout instead of floats to avoid rounding drift in financial calculations."
- "Cached third-party FX rates in Redis with a TTL to cut external API calls and latency."
- "Snapshotted converted amounts at write time so historical expenses aren't retroactively altered by rate changes."
