# Settl. — Smart Expense Splitting & Personal Finance Engine

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg?style=flat-square&logo=openjdk)](https://openjdk.org/)
[![Spring Boot 3.4.3](https://img.shields.io/badge/Spring%20Boot-3.4.3-brightgreen.svg?style=flat-square&logo=springboot)](https://spring.io/projects/spring-boot)
[![PostgreSQL 16](https://img.shields.io/badge/PostgreSQL-16-blue.svg?style=flat-square&logo=postgresql)](https://www.postgresql.org/)
[![Redis 7](https://img.shields.io/badge/Redis-7-red.svg?style=flat-square&logo=redis)](https://redis.io/)
[![React 19](https://img.shields.io/badge/React-19-61DAFB.svg?style=flat-square&logo=react)](https://react.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.9-3178C6.svg?style=flat-square&logo=typescript)](https://www.typescriptlang.org/)
[![Vite](https://img.shields.io/badge/Vite-6.0-646CFF.svg?style=flat-square&logo=vite)](https://vitejs.dev/)
[![Tailwind CSS v4](https://img.shields.io/badge/Tailwind_CSS-v4.0-38B2AC.svg?style=flat-square&logo=tailwind-css)](https://tailwindcss.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=flat-square)](https://opensource.org/licenses/MIT)

**Settl** is a production-grade full-stack expense splitting platform and personal finance tracker designed for groups, roommates, and travel buddies. Settl replaces convoluted web debts with an optimal **$O(N \log N)$ Greedy Max-Heap Debt Simplification Algorithm**, features real-time **Multi-Currency FX conversions** with 3-tier resilient Redis caching, automated **recurring expense scheduling**, and an enterprise-grade **security architecture**.

---

## ⚡ 30-Second Overview for Recruiters & Engineers

- **Core Algorithm**: Custom greedy dual max-heap matching algorithm reducing complex $N$-party peer debts to a minimum transaction set (provably bounded by $\le N - 1$ payments). Exhaustively verified via 50-iteration randomized zero-sum property tests.
- **Resilient Multi-Currency (FX)**: Write-time exchange rate conversions via Frankfurter API with a 3-tier fallback architecture: *Redis 24h Hot Cache* $\rightarrow$ *Redis Fallback Backup* $\rightarrow$ *Hardcoded Fallback Table*. Banker's Rounding (`HALF_EVEN`) ensures penny-exact precision across 30+ ISO-4217 currencies.
- **Security & Session Hardening**: In-memory access tokens (never in `localStorage`), rotating single-use refresh tokens stored in `httpOnly, Secure, SameSite=Strict` cookies, cryptographic token family breach detection (`TOKEN_REUSE_DETECTED`), and an atomic Redis Sorted Set (`ZSET`) sliding-window rate limiter.
- **Personal Budgeting & Analytics**: Private, non-group expense tracking with categorized spending analytics and interactive Recharts category distribution diagrams.
- **Enterprise Test Suite**: 140+ unit, integration, and security tests with 100% pass rate across Flyway migrations, rate limiters, scheduler idempotency, and split engines.

---

## 🏗️ System Architecture

```mermaid
flowchart TD
    subgraph Client ["Frontend (React 19 + TypeScript + Vite)"]
        UI[Tailwind CSS + Responsive Layouts]
        State[Zustand In-Memory Auth + TanStack Query]
        Router[React Router v7 SPA Routing]
    end

    subgraph Edge ["Security & Gateway Layer"]
        RateLimit["@RateLimited Redis Sliding Window (ZSET)"]
        Security["Spring Security 6 (JWT + Breach Detection)"]
    end

    subgraph App ["Backend Services (Spring Boot 3.4.3 / Java 21)"]
        AuthSvc[Auth & Token Rotation Service]
        GroupSvc[Group & Membership Service]
        SplitSvc[Split Engines (Equal, Exact, %, Shares)]
        BalanceSvc[Balance Calculator & Ledger]
        DebtEngine["DebtSimplifier (Greedy Max-Heap)"]
        FxSvc["FxRateService (3-Tier Cache Fallback)"]
        RecurringWorker["@Scheduled Recurring Worker (Idempotent)"]
        AuditSvc[Audit Activity Logger]
    end

    subgraph Data ["Persistence & Cache"]
        Postgres[("PostgreSQL 16\n(Relational Ledger + Flyway)")]
        Redis[("Redis 7\n(Sliding Rate Limits + 24h FX Cache)")]
        Frankfurter[("Frankfurter ECB FX API")]
    end

    Client -->|Bearer JWT + httpOnly Cookie| Edge
    Edge --> Security
    Security --> App
    App --> Postgres
    App --> Redis
    FxSvc -->|HTTP API / 24h TTL| Frankfurter
```

---

## 🛠️ Technology Stack

| Layer | Technology | Rationale |
|---|---|---|
| **Backend** | Java 21 + Spring Boot 3.4.3 | High-throughput enterprise REST architecture, virtual threads support, strong typing for financial mathematics. |
| **Security** | Spring Security 6 + JJWT 0.12.6 | JWT authentication, rotating refresh tokens with token family breach detection, BCrypt password hashing (strength 12). |
| **Database** | PostgreSQL 16 + Flyway | ACID-compliant relational integrity, strict foreign key cascading, version-controlled schema migrations. |
| **Caching & Rate Limiting** | Redis 7 + Lettuce | Atomic Redis `ZSET` sliding window rate limiter, 24h FX cache, session management. |
| **Split & Debt Engines** | Strategy Pattern + Max-Heaps | Strict `BigDecimal` arithmetic (`HALF_UP` / `HALF_EVEN`), penny-exact remainder allocations, $O(N \log N)$ debt simplification. |
| **Frontend** | React 19 + TypeScript + Vite 6 | Lightning-fast dev builds, strict compile-time types matching backend OpenAPI schemas. |
| **State Management** | TanStack Query v5 + Zustand 5 | Server state caching, in-memory volatile authentication state protecting access tokens from XSS. |
| **Styling** | Tailwind CSS v4 + Lucide Icons | Mobile-first responsive design, modern typography, accessible 44px+ tap targets. |
| **Visualizations** | Recharts 3.x | Spending category donut charts, monthly expenditure trends, visual debt transfer graph. |
| **Testing** | JUnit 5 + Mockito + MockMvc | 140+ tests including randomized property-based mathematical invariants and rate limit verification. |
| **Containerization** | Docker + Docker Compose | Multi-stage production builds with minimal Alpine JRE and Nginx static delivery. |

---

## 🧮 The Centerpiece: Debt Simplification Algorithm

When multiple members in a group make payments for meals, trips, or housing, the resulting pairwise debt graph can quickly become an unmanageable tangle of $O(N^2)$ transactions.

Settl implements a greedy max-heap matching algorithm that eliminates circular debts and compresses total transactions down to $\le N - 1$:

```
        COMPLEX PEER DEBTS (5 Transactions)                  SETTL SIMPLIFIED (2 Transactions)
         ┌─────────┐       $40       ┌─────────┐              ┌─────────┐                ┌─────────┐
         │  Bob    ├────────────────►│  Alice  │              │  Bob    ├───────────────►│  Alice  │
         └──┬──────┘                 └────▲────┘              └───┬─────┘     $70        └────▲────┘
            │                             │                       │                           │
        $30 │                             │ $60                   │ $20                       │ $30
            ▼                             │                       ▼                           │
         ┌─────────┐       $10       ┌────┴────┐              ┌─────────┐                     │
         │ Charlie ├────────────────►│  David  │              │  David  ├─────────────────────┘
         └─────────┘                 └─────────┘              └─────────┘
```

### Complexity & Guarantees
- **Time Complexity**: $O(N \log N)$ — constructing heaps in $O(N)$ and executing at most $N-1$ extract/insert operations in $O(\log N)$ time.
- **Space Complexity**: $O(N)$ auxiliary memory for debtor and creditor priority queues.
- **Zero-Sum Conservation**: Total money settled is invariant; every member's balance reaches exactly $0.00$.
- **Formal In-Depth Documentation**: See [docs/algorithm-explainer.md](file:///C:/Users/ombiswas/Documents/Projects/Settl/docs/algorithm-explainer.md) for full mathematical proof and step-by-step trace.

---

## 🌐 Multi-Currency & 3-Tier Resilient FX Engine

Settl allows users to log expenses in any currency (e.g., EUR, GBP, JPY) while settling in the group's default currency (e.g., USD):

1. **Write-Time Conversion**: Real-time conversion using European Central Bank rates via the Frankfurter API.
2. **3-Tier Fallback Strategy**:
   - **Tier 1 (Hot Cache)**: Redis 24-hour cached exchange rates.
   - **Tier 2 (Fallback Cache)**: Redis stale backup cache if live API is temporarily unreachable.
   - **Tier 3 (Hardcoded Table)**: Offline fallback exchange table guaranteeing zero runtime crashes.
3. **Banker's Rounding**: Uses `RoundingMode.HALF_EVEN` to eliminate statistical accumulation bias.

---

## 🔒 Security Architecture Checklist

- [x] **XSS Mitigation**: Short-lived access tokens (15m) are held strictly in memory in Zustand state. Never persisted to `localStorage` or `sessionStorage`.
- [x] **CSRF & Refresh Security**: 7-day refresh tokens are stored in `httpOnly, Secure, SameSite=Strict` cookies inaccessible to JavaScript.
- [x] **Token Family Breach Detection**: Refresh tokens are rotated on each use. If an old refresh token is reused (indicating a compromised token family), the entire family is instantly revoked (`401 TOKEN_REUSE_DETECTED`).
- [x] **Distributed Sliding-Window Rate Limiting**: Atomic Redis `ZSET` sliding window interceptor protecting `/api/auth/*` against brute-force attacks, returning HTTP 429 with `X-RateLimit-*` and `Retry-After` headers.
- [x] **Method-Level Authorization**: All group endpoints enforce `@PreAuthorize` group membership. Only creators or admins can edit/delete expenses.
- [x] **Input Sanitization**: Zero SQL string concatenation (100% parameterized JPA queries) and server-side Jakarta Bean Validation on all DTOs.

---

## 🚀 Getting Started Locally

### Prerequisites
- Java 21 JDK
- Node.js 20+ & npm
- Docker & Docker Compose

### 1. Start Infrastructure (Postgres + Redis)
```bash
# Start PostgreSQL (port 5435) and Redis (port 6379)
docker compose up -d
```

### 2. Run Backend (Spring Boot)
```bash
cd backend

# Run all 140+ unit and integration tests
./mvnw test

# Start the Spring Boot development server
./mvnw spring-boot:run
```
*Backend runs on `http://localhost:8080` (Swagger UI: `http://localhost:8080/swagger-ui.html`)*

### 3. Run Frontend (React + Vite)
```bash
cd frontend

# Install dependencies
npm install

# Start Vite development server
npm run dev
```
*Frontend runs on `http://localhost:5173` with automatic `/api` proxying to backend.*

---

## 🐳 Containerized Production Deployment

### Option A: One-Command Production Stack (Docker Compose)
```bash
# Builds multi-stage JRE backend and Nginx static frontend
docker compose -f docker-compose.prod.yml up -d --build
```

### Option B: Cloud Deployment (Railway / Render + Vercel)
1. **Backend & Database (Railway / Render)**:
   - Deploy PostgreSQL 16 & Redis from template.
   - Deploy backend using `backend/Dockerfile`.
   - Set environment variables:
     ```ini
     SPRING_PROFILES_ACTIVE=prod
     DB_URL=jdbc:postgresql://<db-host>:5432/<db-name>?options=-c%20timezone=UTC
     DB_USER=<db-user>
     DB_PASSWORD=<db-password>
     REDIS_HOST=<redis-host>
     REDIS_PORT=6379
     JWT_SECRET=<64-char-base64-random-string>
     JWT_REFRESH_SECRET=<64-char-base64-random-string>
     APP_BASE_URL=https://your-settl-app.vercel.app
     ```
2. **Frontend (Vercel / Netlify)**:
   - Connect repository pointing root to `frontend/`.
   - Build Command: `npm run build`
   - Output Directory: `dist`
   - `frontend/vercel.json` and `frontend/public/_redirects` automatically handle SPA routing rewrites.

---

## 📊 Testing & Verification

Settl is backed by a comprehensive automated test suite:

```bash
cd backend
./mvnw test
```

```
[INFO] Results:
[INFO] 
[INFO] Tests run: 140, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] BUILD SUCCESS
```

- **Randomized Invariant Tests**: 50 iterations verifying $N$-group zero-sum conservation and settlement transaction minimization.
- **Scheduler Idempotency**: Proves background recurring expense runner never duplicates charges.
- **Breach Detection Tests**: Simulates token theft and validates immediate token family invalidation.
- **Brute-Force Integration Tests**: Validates that exceeding 5 rapid login attempts triggers HTTP 429.

---

## 📄 License

Distributed under the MIT License. See `LICENSE` for more information.
