# KingsFarm Backend — Development Plan

This is the working plan for turning the KingFarm frontend (currently a React SPA with
all data living in-memory in `FarmProvider`/`store.tsx`, reset on every refresh) into a
real client/server application backed by this Spring Boot + MySQL project. Every module,
rule, and edge case implemented in the frontend during our earlier sessions is captured
here so nothing gets dropped in translation. This file is the source of truth for the
build — update it as decisions change.

Status: **Phase 0 complete** (Spring Boot connected to local MySQL). Nothing beyond that
has been built yet — this document is the plan, not a changelog.

---

## 1. Goals

- Replace every piece of in-memory frontend state with real persisted data in MySQL,
  reachable only through authenticated REST endpoints.
- Preserve **all** existing frontend behavior exactly: same-day edit locks, Opening
  Stock carry-forward + admin approval flow, running credit/advance balances, per-record
  attribution (`enteredBy`/`updatedBy`, Administrator-only visible), multi-manager
  shared visibility per module, year-restricted history for managers vs. full history
  for Administrators, admin-only export, customer opening balances, visit-frequency
  filtering, and the cross-module automatic data flows (Bird Stock → Production →
  Whole Egg → Production → Crack Egg → Production, Feed Mill → Fish Feed).
- This is an **internal-only** application — no public sign-up. Only the Administrator
  creates accounts, with a default password the user must change on first login.
- Every list endpoint paginated. No unbounded queries.
- JWT-based authentication on every protected endpoint, plus a shared API key required
  on every request so a random client can't reach the API at all, even before auth is
  considered.
- Database schema properly normalized and indexed — not a 1:1 dump of the frontend's
  flat JS objects.

---

## 2. Tech Stack

- Java 26 / Spring Boot 4.1.1 (already set up) / Maven
- MySQL (local via Homebrew, already connected) via Spring Data JPA / Hibernate
- Spring Security (to add) — JWT resource-server-style stateless auth
- `spring-boot-starter-validation` (to add) — Bean Validation on every request DTO
- `springdoc-openapi-starter-webmvc-ui` (recommended) — Swagger UI so endpoints are
  browsable/testable while we build, matches "I want to see what I'm doing"
- `jjwt` (io.jsonwebtoken) (to add) — JWT signing/verification
- Lombok (already present)
- BCrypt (comes with Spring Security) for password hashing

---

## 3. Security Design

**Layered so a random internet client is rejected before auth is even checked:**

1. **API key filter** (first in the chain) — every request must carry a shared-secret
   header (e.g. `X-API-Key`). Value lives in an environment variable
   (`API_SHARED_KEY`), never committed. Requests without it get `401` immediately,
   before touching Spring Security or the database. This is the "outside client can't
   even reach the server" layer you asked for.
2. **JWT auth filter** — after the API key check, every protected endpoint requires
   `Authorization: Bearer <token>`. Stateless (no server-side session).
   - `POST /api/auth/login` — username + password → short-lived **access token**
     (~30 min) + longer-lived **refresh token** (~7 days).
   - `POST /api/auth/refresh` — exchange a valid refresh token for a new access token.
   - `POST /api/auth/logout` — revokes the refresh token (stored server-side so it can
     be invalidated; access tokens are short-lived enough not to need a blocklist).
   - Access token claims: `sub` (username), `role`, `userId`. Kept minimal.
3. **Role-based authorization** — `@PreAuthorize` on every controller method, matching
   the frontend's `ACCESS` map exactly (see §5.1). A Whole Egg Manager's token can
   never reach `/api/mortality/**`, etc. Administrator reaches everything.
4. **Field-level filtering, not just UI hiding** — right now the frontend just hides
   `enteredBy`/`updatedBy` from non-admins in the UI; the data is still sent to the
   browser. The backend must actually not serialize those fields for non-Administrator
   callers (separate DTO projections, or a Jackson view/filter keyed off the caller's
   role). Same for anything else that's currently "hidden," not "protected."
5. **Password policy & lockout** — BCrypt hashing, minimum length enforced from
   `SecuritySettings.passwordMinLength` (admin-configurable, mirrors the frontend's
   existing Security Settings panel), account lockout after
   `SecuritySettings.lockoutAttempts` failed logins within a window, session/token
   timeout from `SecuritySettings.sessionTimeout`.
6. **Default password + forced change** — when the Administrator creates a user, a
   default password is generated (or admin sets one), and the account carries
   `mustChangePassword = true`. Every authenticated endpoint except
   `POST /api/auth/change-password` is blocked until that flag is cleared. This is the
   exact flow you described: "admin creates the user and gives them default login
   details."
7. **CORS** locked to the known frontend origin(s) only, configured via env var, not
   wildcarded.
8. **Actuator lockdown** — `spring-boot-starter-actuator` is already a dependency;
   restrict exposed endpoints (`management.endpoints.web.exposure.include`) to
   `health` only, and put even that behind the API key.
9. **Centralized error handling** — a `@ControllerAdvice` returning a consistent
   `{ timestamp, status, error, message, path }` shape, never leaking stack traces or
   raw exception messages to the client.
10. **Audit logging** — every create/update/delete and every login attempt is written
    to the `system_logs` table (see §5.9), not just displayed from static demo data
    like today.
11. **Secrets** — DB password, JWT signing secret, and the API shared key all move to
    environment variables (`application.yaml` already flags the DB password for this;
    same treatment applies once auth is in).

---

## 4. Roles & Access Map (from `shared.ts`, preserved exactly)

```
Administrator      → everything (bird-stock, production, whole-egg, crack-egg,
                      feed-mill, mortality, admin, dashboard, profile)
Production Manager → bird-stock, production, dashboard, profile
Whole Egg Manager  → whole-egg, dashboard, profile
Crack Egg Manager  → crack-egg, dashboard, profile
Feed Mill Manager  → feed-mill, dashboard, profile
Mortality Manager  → mortality, dashboard, profile
Managing Director  → general-report (read-only, farm-wide), dashboard, profile
```

Multiple accounts can share the same role (e.g. two Feed Mill Managers) — the backend
scopes data by **module**, not by individual user, so they see each other's records.
This falls out naturally once data lives in shared tables instead of per-session
browser state; no extra multi-tenancy logic needed.

**Relief Access**: a temporary grant letting one manager cover another module while its
regular manager is away. While active, the person on leave cannot log in, and the
grantee gets that module's access added on top of their own. Needs its own table and
endpoints (§5.10) — Administrator-only to create/revoke.

---

## 5. Domain Model

Normalized relational design — line-item detail (egg category quantities/prices) goes
into child tables rather than 6-12 flat columns per row, so it can be queried, indexed,
and reported on properly.

### 5.1 Users & Auth
- `users`: id, username (unique), password_hash, full_name, role (enum), active,
  must_change_password, created_at, created_by, last_login_at
- `refresh_tokens`: id, user_id (FK), token_hash, issued_at, expires_at, revoked_at
- `security_settings`: singleton row — session_timeout_minutes, lockout_attempts,
  password_min_length
- `relief_grants`: id, on_leave_user_id (FK), grantee_user_id (FK), reason,
  granted_by (FK), granted_at, active, revoked_at, revoked_by (FK)

### 5.2 Customers (Whole Egg directory)
- `customers`: id, first_name, last_name, phone, state, lga, street, created_at,
  created_by (FK users)

### 5.3 Whole Egg
- `we_sale_transactions`: id, txn_date, txn_year, txn_time, customer_id (FK),
  customer_name_snapshot, state, type (enum: SALE/PAYMENT/OPENING), payment_methods
  (set/CSV), bank, cash_amount, transfer_amount, amount_paid, credit, advance,
  entered_by (FK users), updated_by (FK users)
- `we_sale_line_items`: id, transaction_id (FK), category (enum xL/lg/md/sm/pl/wh),
  qty, price — replaces the 6-category flat columns; only categories actually sold get
  a row
- `we_opening_stock` / reuse the generic `opening_stock_state` table (§5.8) keyed by
  module="whole-egg", scope=category
- `we_prices`: category (PK), price — current selling price per category, editable by
  Whole Egg Manager, admin locked like everything else if we want (currently isn't
  lock-restricted in the frontend, just role-restricted)
- `we_sales_crack`, `we_gift`: category totals committed from the Sales Crack & Gift
  tab — these are running totals, not per-record logs, matching frontend behavior
  exactly (`commitWeCrack`/`commitWeGift`)

### 5.4 Crack Egg
- `ce_sale_transactions`: id, txn_date, txn_time, customer (free text — Crack Egg never
  got the customer-directory treatment Whole Egg did), state, qty, price,
  payment_methods, bank, cash_amount, transfer_amount, amount_paid, credit, advance,
  entered_by, updated_by
- `ce_gift_log`: id, txn_date, txn_time, qty, recipient, authorizer, entered_by,
  updated_by
- `ce_stock_state`: good/rough crack opening, gift qty/recipient/authorizer, selling
  price, rough-crack feed-mill usage — mirrors `gcOpening`, `gcSellingPrice`,
  `gcGiftQty`, `rcOpening`, `rcFeedMill` etc.

### 5.5 Mortality
- `mort_pen_entries`: id, entry_date, pen, category (enum Good/Dry/Runt/Green/PM-
  Reject), mortality_count, entered_by, updated_by — same-day-lock pattern
- `mort_sale_entries`: id, txn_date, txn_time, category, qty, price, payment_methods,
  bank, cash_amount, transfer_amount, amount_paid, credit, advance, entered_by,
  updated_by
- `mort_gift_log`: id, txn_date, txn_time, good, dry, runt, recipient, authorizer,
  entered_by, updated_by
- `mort_catfish_disposal`: id, entry_date, category (Green/PM-Reject), catfish_qty,
  disposal_qty, entered_by, updated_by

### 5.6 Feed Mill
- `feed_ingredients`: id, name, unit, min_threshold
- `feed_ingredient_stock_daily`: id, ingredient_id (FK), stock_date, opening, added,
  used — daily figures, opening-locked like everywhere else
- `feed_formulations`: id, feed_type, ingredient_id (FK), qty_per_ton — current live
  formulation
- `feed_formulation_history`: id, saved_at, feed_type, changed_by (FK users) — one row
  per Save Formulation click
- `feed_formulation_history_items`: id, history_id (FK), ingredient_id (FK), old_val,
  new_val
- `feed_production_log`: id, prod_date, prod_time, feed_type, qty_tons, formulation_ref,
  entered_by, updated_by
- `feed_fish_stock`: id, fish_feed_type, stock_date, opening, added, collected
- `feed_collection_log`: id, collected_at, collected_by (Farm/Prince — a location
  label, not a user), fish_starter_kg, fish_grower_kg, fish_finisher_kg, entered_by,
  updated_by

### 5.7 Bird Stock & Production
- `bird_pen_records`: id, entry_date, pen, opening, mortality, bird_sales, restocking,
  remarks, entered_by, updated_by
- `production_pen_entries`: id, entry_date, pen, category, qty, entered_by, updated_by
- `production_crack_state`: entry_date, good_open, rough_open, good_prod, rough_prod,
  good_classify, rough_classify

### 5.8 Opening Stock Locking (cuts across every module)
- `opening_stock_state`: id, module, scope (unique per module+scope), locked
  (boolean, default true once ever set) — generic table backing
  `isOpeningLocked`/`lockOpeningField` for every module (pens, ingredients, egg
  categories, crack stock, fish feed) instead of one-off per-module logic
- `opening_stock_requests`: id, module, scope, scope_label, requested_by (FK),
  reason, requested_at, status (PENDING/APPROVED/DENIED), resolved_at, resolved_by

### 5.9 Admin Logs
- `system_logs`: id, log_type (ACCESS/ACTIVITY/AUDIT), occurred_at, user_id (FK),
  module, action, detail, status, ip_address — one table, indexed on
  `(log_type, occurred_at)`, replaces the three static demo arrays
  (`ACCESS_LOG`/Activity/Audit) with real rows written as things actually happen

### 5.10 Relief Access
Covered under §5.1 (`relief_grants`) — Administrator create/revoke endpoints, and a
check in the login flow: while `relief_grants.active = true` for a user's own account,
their login is blocked ("on leave"); while active for their role as *grantee*, their
JWT/authorization check additionally allows the on-leave manager's module.

---

## 6. Cross-Module Data Flow

The frontend's `FarmProvider` currently wires these transfers live in memory:

```
Bird Stock  →  Production        (Closing Bird Stock → Production %)
Production  →  Whole Egg         (Total Production → Egg Production opening)
Whole Egg   →  Production        (Sales Crack, Sales, Gift → Crack Use / Sales / Gift)
Production  →  Crack Egg         (Good/Rough classification → Crack Received)
Crack Egg   →  Production        (Good Crack Gift & Sales → Production's figures)
Feed Mill production → Fish Feed stock (already wired, stays inside Feed Mill module)
```

Server-side, these become either (a) computed read-time joins/aggregations rather than
duplicated stored values, or (b) triggered writes on the relevant save action — needs a
per-flow decision during Phase 3, but the values must never drift out of sync the way
they can't in the current in-memory model.

---

## 7. API Conventions

- REST, JSON, versioned base path `/api/v1/...`
- Grouped per module: `/api/v1/auth`, `/api/v1/admin/users`,
  `/api/v1/admin/relief-access`, `/api/v1/admin/logs`, `/api/v1/admin/security-settings`,
  `/api/v1/bird-stock`, `/api/v1/production`, `/api/v1/whole-egg`,
  `/api/v1/whole-egg/customers`, `/api/v1/crack-egg`, `/api/v1/mortality`,
  `/api/v1/feed-mill`, `/api/v1/reports/{module}`, `/api/v1/reports/general`
- **Every list endpoint paginated** via `?page=&size=&sort=`, response shape:
  ```json
  { "content": [...], "page": 0, "size": 20, "totalElements": 137, "totalPages": 7 }
  ```
- Consistent error shape from the global `@ControllerAdvice` (see §3.9)
- Bean Validation (`@Valid`) on every request DTO — required fields, min/max, enum
  values — so bad data never reaches the service layer

---

## 8. Reports — a real upgrade, not just a port

Today's Reports tabs (Today/Week/Month/Quarter/Half-Year/Year) run entirely on
pre-authored synthetic data, because the frontend has never had real historical
storage — even "Entered By" there is a deterministic fake value, by design (see the
memory note on this). **Once records actually persist in MySQL, Reports should become
real aggregation queries** (`SUM`/`COUNT`/`GROUP BY` over date ranges) instead of
synthetic data. This is a meaningful upgrade beyond a straight port:
- Real Entered By + timestamp per period, no synthetic fallback needed.
- The customer visit-frequency filter becomes genuinely meaningful once purchases
  span real different days, instead of everything always being "today."
- Drill-down (day → transactions) becomes a real query instead of reusing the flat
  Today table.

Flagged as an open decision in §11 — confirm before Phase 4.

---

## 9. Frontend Integration Plan

Once endpoints exist, the frontend's `store.tsx`/`FarmProvider` gets replaced module by
module:
- Replace `useState` arrays with data fetched from the API (React Query recommended
  for caching/pagination/mutations, but plain `fetch`+`useEffect` works too).
- `App.tsx`'s `registeredUsers`/`handleLogin` gets replaced by real
  `POST /api/v1/auth/login`, storing the JWT (e.g. in memory + refresh token in an
  httpOnly-cookie-equivalent, or localStorage if we accept the tradeoff — worth a
  quick decision when we get there) and attaching `Authorization` + `X-API-Key` headers
  to every request.
- `usePagination` (client-side slicing today) gets replaced by real server-driven
  pagination params.
- Every `isAdmin && (...)` UI gate stays (good UX either way), but the underlying data
  the API returns to a non-admin caller genuinely won't include the gated fields
  anymore.

This phase is its own significant chunk of work, not a footnote — flagged as Phase 5.

---

## 10. Phased Roadmap

- **Phase 0 — Infra** ✅ done: Spring Boot ↔ local MySQL connected.
- **Phase 1 — Auth & Users**: `users`, `refresh_tokens`, `security_settings` entities;
  API key filter; JWT login/refresh/logout; admin user CRUD with default
  password + forced change; role-based `@PreAuthorize` scaffolding; global error
  handling; Swagger UI wired up.
- **Phase 2 — Shared building blocks**: pagination DTO/utility; generic
  `opening_stock_state`/`opening_stock_requests` tables + endpoints; audit-logging
  aspect writing to `system_logs`; Bean Validation conventions.
- **Phase 3 — Modules**, one at a time, each with entities + endpoints + same-day
  edit lock + attribution enforcement (recommended order — simplest/most foundational
  first): Bird Stock → Production → Whole Egg (incl. customers + opening balance) →
  Crack Egg → Mortality → Feed Mill.
- **Phase 4 — Reports**: real aggregation endpoints per module + general report;
  Relief Access endpoints; Admin logs backed by real `system_logs` rows.
- **Phase 5 — Frontend integration**: swap `FarmProvider`/`App.tsx` over to the API,
  module by module, verifying each screen still behaves exactly as it does today.
- **Phase 6 — Security & hardening pass**: rate limiting, CORS review, actuator
  lockdown, secrets audit, dependency check, a pass through every endpoint confirming
  `@PreAuthorize` matches §4 exactly.

---

## 11. Open Decisions (need your input before or during the relevant phase)

1. **Reports**: confirm real DB aggregation (§8) is wanted now, vs. keeping the
   synthetic-data approach a while longer and porting it as-is first.
2. **Token storage on the frontend**: localStorage (simpler, some XSS exposure) vs. an
   httpOnly cookie set by the backend (more setup, better protection) for the JWT.
3. ~~Crack Egg customer~~ — **decided**: stays free-text, no customer directory or
   running-balance treatment. Whole Egg keeps that feature exclusively.
4. ~~Default password delivery~~ — **decided**: the system auto-generates a random
   default password on user creation and returns it once in the create-user response
   for the admin to relay to the new user. Never stored or retrievable in plain text
   again after that.
