# KingsFarm Backend — Development Plan

This is the working plan for turning the KingFarm frontend (currently a React SPA with
all data living in-memory in `FarmProvider`/`store.tsx`, reset on every refresh) into a
real client/server application backed by this Spring Boot + MySQL project. Every module,
rule, and edge case implemented in the frontend during our earlier sessions is captured
here so nothing gets dropped in translation. This file is the source of truth for the
build — update it as decisions change.

Status: **Phase 0 and Phase 1 complete** (infra + JWT/API-key auth, admin user
management with auto-generated passwords, security settings, bootstrap admin seed).
**Phase 2 complete** (generic opening-stock locking + approval workflow, unified
system_logs table, `@Audited` AOP aspect, login/logout access logging). **Phase 3
complete**: Bird Stock, Production, Whole Egg, Crack Egg, Mortality, and Feed Mill are
all done — every module has entities, endpoints, same-day/opening-stock locking, and
attribution. **Phase 4 complete**: Relief Access (login blocking + JWT extra-authority grants);
Admin Logs were already real since Phase 2; Reports (§8) now run on real DB
aggregation for all six modules plus the General Report — `/reports/daily`,
`/reports/monthly` per module, `/api/v1/reports/general` for the farm-wide summary.
**Phase 5 (frontend integration) and Phase 6 (security hardening) remain.** This
document is the plan, not a changelog — update it as decisions change, but treat it as
living documentation, not history.

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

### 5.5 Mortality — as built (see Phase 3 roadmap entry below for the full reasoning)
- `mort_category_values`: id, category (unique), opening (opening-stock-locked),
  gift_qty (overwritten, not accumulated) — the only two truly-live scalars;
  everything else on Stock Overview is computed at read time
- `mort_pen_entries`: id, pen_id (FK), entry_date, good, dry, runt, green,
  pm_reject, entered_by, updated_by — unique on (pen_id, entry_date), same-day-lock
  pattern, no carry-forward
- `mort_sale_entries` (+ `mort_sale_entry_payment_methods`): id, category, qty,
  price, customer, state, payment methods, bank, cash_amount, transfer_amount,
  amount_paid, credit, advance (hand-typed, not derived), occurred_at, entered_by,
  updated_by
- `mort_gift_log`: id, good, dry, runt, recipient, authorizer, occurred_at,
  entered_by, updated_by
- `mort_catfish_disposal_state`: id, entry_date (unique), catfish_qty, disposal_qty,
  entered_by, updated_by — one combined row per day, not split by category

### 5.6 Feed Mill — as built (see Phase 3 roadmap entry below for the full reasoning)
- `feed_ingredients`: id, name (unique), unit, opening (opening-stock-locked), added,
  used, min, entered_by, updated_by — no day dimension at all, a single
  always-current row per ingredient (§5.3 pattern), not `feed_ingredient_stock_daily`
  as originally sketched here
- `feed_types`: id, name (unique) — extensible catalog, no soft-deactivate
- `feed_formulation_entries`: id, feed_type_id (FK), ingredient_id (FK), qty_per_ton
  (live value), last_saved_qty_per_ton (durable substitute for the frontend's
  ephemeral `formBaseline`) — unique on (feed_type_id, ingredient_id), sparse
- `feed_formulation_history_groups`: id, feed_type_id (FK), changed_by, occurred_at —
  one row per "Save Formulation" click that changed something
- `feed_formulation_history_items`: id, group_id (FK), ingredient_id (FK), old_val,
  new_val
- `feed_production_log`: id, feed_type_id (FK), qty_tons, formulation_ref (nullable,
  never auto-generated — see reasoning below), occurred_at, entered_by, updated_by
- `fish_feed_stock`: id, type (unique — always exactly "Fish Starter"/"Fish
  Grower"/"Fish Finisher"), opening (opening-stock-locked), added (auto-transfer
  only), collected (accumulated only) — same no-day-dimension pattern as ingredients
- `feed_collection_log`: id, collected_by (Farm/Prince — a location label, not a
  user), fish_starter_kg, fish_grower_kg, fish_finisher_kg, occurred_at, entered_by,
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

**Decided (§11 item 1): real DB aggregation, built during Phase 4.** Today's Reports
tabs (Today/Week/Month/Quarter/Half-Year/Year) ran entirely on pre-authored synthetic
data in the frontend, because the demo never had real historical storage — even
"Entered By" there was a deterministic fake value, by design. Now that every module's
records actually persist in MySQL (Phase 3), Reports is backed by real
`SUM`/`COUNT`/`GROUP BY` aggregation over date ranges.

**This is deliberately not a literal port of the frontend's six-bucket shape.** A full
read of `ReportsPanel.tsx` (973 lines) showed the real consumption pattern isn't "6
independent period buckets" — `data.monthly.tableRows` is essentially dead weight: the
table body for a *month view* is actually synthesized client-side by cycling the
*weekly* bucket's 7-day pattern across the month (`generateDaysForMonth`), a hack that
only existed because the demo had no real per-day history to draw a real month from.
Only `data.monthly.stats` (the four stat cards) was ever genuinely consumed from that
bucket. Every period's top-level stat cards are already re-derivable from row data —
`ReportsPanel` already has an `aggregateStats` helper that does exactly this for custom
date ranges today.

So instead of 6 buckets, every module (Bird Stock, Production, Whole Egg, Crack Egg,
Mortality, Feed Mill) exposes exactly **two** new read endpoints, both under
`/reports/`:
- **`GET /reports/daily?start=&end=`** — one row per calendar day in the (inclusive,
  ≤366-day) range. Feeds the Week tab and any custom range; the frontend derives its
  own stat cards from the rows via `aggregateStats`, same as it does for custom ranges
  today.
- **`GET /reports/monthly?months=N`** — one row per trailing calendar month, N from 1
  to 24. Feeds Quarter (`N=3`), Half-Year (`N=6`), and Year (`N=12`) — a real
  month-by-month breakdown, not a day-cycling hack.
- The **Today** per-entity tab (per pen / per category / per feed type, for today
  specifically) is **not** a new endpoint — it reuses each module's existing Phase 3
  "today" endpoint (`/mortality/stock`, `/bird-stock/records/today`,
  `/feed-mill/ingredients`, etc.), since that data already exists in exactly the right
  shape.
- Export to Excel/PDF/Word and the trend chart are entirely frontend-side (xlsx/jspdf/
  docx/recharts) — no backend involvement, then or now.

Both endpoints return the same shared shape, `common.reports.ReportTableResponse`
(`{columns: [{key,label,mono}], rows: [{period, ...metric: value}]}`) — plain
`LinkedHashMap` rows keyed by plain `String`s, so none of the enum-key Jackson
ambiguity that motivated `List<Entry>` DTOs elsewhere applies here. Row/period
generation (day list, trailing-month list, day/month display labels, `LocalDate`↔
`Instant` range conversion) is centralized in `common.reports.ReportPeriods`.

**Known real-data gap, by module** (flagged rather than faked): several modules only
store a *live running total* for stock levels, with no per-day historical snapshot —
`WeCategoryValue` (Whole Egg), `CrackEggState` (Crack Egg, singleton row),
`MortCategoryValue` (Mortality), `FeedIngredient`/`FishFeedStock` (Feed Mill). A
"closing stock as of date X" figure for these can't be queried, only reconstructed by
replaying every delta up to that date — expensive and out of scope for this pass. The
report columns below only include metrics with a genuine per-day (or per-instant) log
to sum; columns the frontend's synthetic data implied but that have no real historical
source (Crack Egg's `roughToFeed`/`closing`, Feed Mill's `alerts`) are dropped rather
than faked. Current-state equivalents (live low-stock ingredients, today's closing
stock) remain available via each module's existing Phase 3 endpoints.

Per-module column sets (same columns at both daily and monthly granularity):
- **Bird Stock**: `opening, mortality, sales, restock, closing` — summed/recomputed
  from `BirdPenRecord` across all pens for each day (closing is `BirdPenRecord.closing()`
  summed, a pure function of stored ints, so this one *does* have real historical
  closing stock, unlike the running-total modules above).
- **Production**: `crates, pct, xL, lg, md` — summed from `ProductionPenEntry` per day;
  `pct` reuses `ProductionService.productionPercent(crates, birdClosing)` with
  `birdClosing` from Bird Stock's real closing total for that same day.
- **Whole Egg**: `txns, crates, revenue, avg` — from `WeSaleTransaction`/`WeSaleLineItem`
  where `type = SALE`, grouped by day; `revenue = sum(qty * price)` (matches
  `WeSaleTransactionResponse`'s live computation).
- **Crack Egg**: `goodSales, goodRevenue, gifts` — `goodSales`/`goodRevenue` from
  `GcSaleTransaction` (`qty`, `qty*price`), `gifts` from `CrackEggGiftLogEntry.qty`.
  `roughToFeed` and `closing` dropped (no historical log — see gap note above).
- **Mortality**: `total, sales, salesRevenue, gifts, catfish, disposal` — `total` from
  `MortPenEntry` (sum of `total()` across pens/day), `sales`/`salesRevenue` from
  `MortSaleEntry`, `gifts` from `MortGiftLogEntry` (sum of `good+dry+runt`), `catfish`/
  `disposal` from `MortCatfishDisposalState` (already keyed by `entryDate`, a real
  per-day row).
- **Feed Mill**: `produced, ingrUsed, fishCollected` — `produced` (tons) from
  `FeedProductionLogEntry.qtyTons`, `fishCollected` from `FeedCollectionLogEntry.total()`;
  `ingrUsed` is derived (production qty × that feed type's current formulation
  `qtyPerTon`, replayed over each range-filtered production row — the same math
  `runProduction` uses live, applied historically) since there's no per-day ingredient-
  usage log, only a live cumulative `FeedIngredient.used`. `alerts` dropped (low-stock is
  a live-only concept, no historical snapshot — see gap note above).

**General Report** (`GeneralReportView.tsx`) is structurally different from the other
six — its rows are "one row per module" (Production/Whole Egg/Crack Egg/Mortality/Feed
Mill), not "one row per day," for whatever date range is selected. It gets its own
top-level `GET /api/v1/reports/general?start=&end=` endpoint (new `reports` package,
Administrator-only, matching `canExport={false}` and its "Managing Director" framing in
the frontend) that composes each module's own range-sum logic rather than re-deriving
it — one row per module with `revenue`/`sales`/`share` (share = that module's revenue as
a % of total revenue across all modules in range).

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
- **Phase 1 — Auth & Users** ✅ done: `users`, `refresh_tokens`, `security_settings`
  entities; `ApiKeyFilter` + `JwtAuthenticationFilter` + `SecurityConfig`; JWT
  login/refresh/logout/change-password; admin user CRUD with auto-generated password
  + forced change (`MustChangePasswordInterceptor`); role-based `@PreAuthorize`
  scaffolding; global error handling. (Swagger UI still deferred — see note below.)
- **Phase 2 — Shared building blocks** ✅ done: `PageResponse` (from Phase 1); generic
  `opening_stock_state`/`opening_stock_requests` tables + `OpeningStockController`
  (managers request, Administrator resolves — approval unlocks the field); unified
  `system_logs` table + `SystemLogService` + Administrator-only `SystemLogController`;
  `@Audited` annotation + `AuditLoggingAspect` (AOP, writes ACTIVITY/AUDIT rows on
  annotated service methods — applied to user-admin actions and opening-stock
  requests/resolutions so far); login/logout now write real ACCESS rows via
  `AuthService`. Bean Validation conventions (`@Valid` + Jakarta annotations on every
  request DTO) already established in Phase 1, reused here.
  - Note: added a `Mod` → wire-string `Converter` (`ModConverter`) since Spring MVC's
    default enum binding for `@RequestParam`/`@PathVariable` doesn't know about the
    `@JsonValue`/`@JsonCreator` mapping used for JSON bodies — worth remembering for
    any other enum that needs to appear in a query string or path.
  - `RequestStatus`/`LogType` still serialize as plain uppercase Java enum names
    (`"PENDING"`, `"ACCESS"`) rather than a frontend-matching wire format — nothing
    in the frontend consumes these yet, so this is deliberately deferred to Phase 5
    rather than guessed at now.
- **Phase 3 — Modules**, one at a time, each with entities + endpoints + same-day
  edit lock + attribution enforcement (recommended order — simplest/most foundational
  first): **Bird Stock ✅ done** → **Production ✅ done** → **Whole Egg ✅ done** →
  **Crack Egg ✅ done** → **Mortality ✅ done** → **Feed Mill ✅ done — Phase 3 complete**.
  - Bird Stock: `Pen` (catalog, soft-deactivate not hard-delete) + `BirdPenRecord`
    (one row per pen per day, unique on pen+date). Closing is never stored — always
    computed as `opening - mortality - birdSales + restocking`. A new day's row is
    found-or-created on read, carrying Opening forward from the prior day's Closing
    automatically, and locking it via `OpeningStockLockService` the same way the
    frontend's `OpeningStockCell` expects. Same-day edit lock is enforced by comparing
    `entryDate` to `LocalDate.now()` — collapses the frontend's separate
    save/unlock-for-today toggle into one rule, since the backend persists on every
    field write instead of on an explicit "Save" click; Phase 5 frontend work should
    adapt the UI to autosave-per-field rather than porting the old toggle literally.
    Reads open to Administrator + Production Manager; writes are Production Manager
    only (Administrator is view-only here, same as the frontend's banner).
  - Production: `ProductionPenEntry` (per pen/day, flat qty columns per category —
    same reasoning as BirdPenRecord) + `ProductionDayState` (one row per day covering
    every scalar field: catOpening ×6 categories, crack good/rough open/prod, good/
    rough classify). Added a shared `CatKey` enum (`common` package, mirrors
    shared.ts's CAT_KEYS/CAT_LABELS/CRACK_WEIGHTS) plus its own wire-format
    `Converter` (same reasoning as `ModConverter`). catOpening is opening-stock-locked
    per category; crackGoodOpen/crackRoughOpen are carried forward daily too but
    deliberately NOT run through the lock — the frontend wraps catOpening in
    `OpeningStockCell` but uses a plain `NumInput` for those two, so that's preserved
    exactly rather than "fixed." Whole Egg → Production and Crack Egg → Production
    auto-feeds (crack use, sales, gift) are stubbed at zero in `ProductionService`
    with clear TODOs — replace once those modules exist, in the order the roadmap
    already specifies.
  - Whole Egg: `Customer` directory (with one-time Opening Balance seeding — an
    "opening"-type `WeSaleTransaction` with zero qty/prices, same pattern as a debt
    payment) + `WeCategoryValue` (one small table, `kind` discriminator
    OPENING/PRICE/SALES_CRACK/GIFT × category, unique per pair — these are running
    totals the frontend keeps as always-current single values with no day dimension,
    per this document's own §5.3, not per-day snapshots like Bird Stock/Production)
    + `WeSaleTransaction` (sale/payment/opening, one canonical `occurredAt` timestamp
    plus an indexed `txnYear` for the year-restricted-history requirement) +
    `WeSaleLineItem` (sparse — one row per category actually sold). Credit/advance
    are always computed server-side from amountPaid vs. what's owed (this
    transaction's total plus whatever balance carried in from the customer's single
    most recent transaction — a running balance, never summed across history) —
    ported from submitSale/RecordPaymentModal/EditSaleTxnModal exactly, including
    `priorBalanceFor` (the transaction immediately before this one for the same
    customer). Per-transaction same-day edit lock (not a whole-day "Save Record" lock
    like Bird Stock/Production) — each sale/payment/opening row is independently
    editable only on the day it was made. Category request/response DTOs use a
    `List<CategoryXEntry>` pattern instead of `Map<CatKey,V>` — Jackson's
    `@JsonValue`/`@JsonCreator` on enum keys inside a Map is genuinely ambiguous
    across Jackson versions, so this sidesteps it entirely rather than gambling on
    unverifiable (sandbox can't compile) behavior. Whole Egg → Production
    (crack use/sales/gift) is genuinely bidirectional with Production → Whole Egg
    (Total Production feeds Whole Egg's Stock Overview) — `ProductionService` injects
    `WholeEggService` as `@Lazy` to break the constructor-injection cycle. Field-level
    admin gating of enteredBy/updatedBy (§3.4) is still not implemented anywhere,
    Whole Egg included — deliberately deferred to the Phase 6 hardening pass rather
    than retrofitted per-module mid-build; flagging again here so it isn't missed.
  - Crack Egg: `CrackEggState` — a **singleton row** (id always 1), not a per-day
    table, covering every "current value" scalar on CrackEggView (gcOpening,
    gcSellingPrice, gcGiftQty/recipient/authorizer, rcOpening, rcFeedMill) — matches
    §5.3's "running total, not per-day snapshot" pattern, same reasoning as Whole
    Egg's `WeCategoryValue`. Deliberately kept independent of `ProductionDayState`'s
    own `crackGoodOpen`/`crackRoughOpen` fields even though they look related — the
    frontend itself reads two separate, never-synced `useState` values for these
    (ProductionView vs CrackEggView), so the backend preserves that rather than
    "fixing" it. `GcSaleTransaction`'s `credit`/`advance` are hand-typed by staff and
    persisted as-is (unlike Whole Egg's `WeSaleTransaction`, where they're always
    server-computed from a running balance) — ported deliberately differently per
    `CESaleTxn`'s own frontend comment ("Manually entered by staff — not calculated
    by the system"). Gift qty vs Feed Mill usage have an intentional validation
    asymmetry, ported exactly from the two Save buttons in `CrackEggView.tsx`: Feed
    Mill's Save is hard-blocked server-side (`BadRequestException`) once usage would
    exceed available Rough Crack stock, while Gift only shows a soft visual warning
    in the frontend and has no backend validation at all. `CrackEggGiftLogEntry` is a
    permanent snapshot taken on "Save Gift" (reads `CrackEggState`'s live gift fields
    at that moment) — editing a past log entry never touches the live state, matching
    the frontend's `giftLog` exactly. Crack Egg ↔ Production is bidirectional the
    same way Whole Egg ↔ Production is: `ProductionService` reads Crack Egg's
    `goodGiftQty()`/`goodSalesQty()` via a `@Lazy CrackEggService` dependency (same
    pattern as its `@Lazy WholeEggService`), while `CrackEggService` reads
    Production's `getTodayDayState()` for `crackGoodProd`/`goodClassify`/
    `crackRoughProd`/`roughClassify` non-lazily. Customer stays free text per §11
    decision #3 — no directory, no running balance.
  - Mortality: **self-contained, no cross-module feed in or out** — the only Phase 3
    module like that. New `MortCat` enum (Good/Dry/Runt/Green/PM-Reject — unrelated
    to `CatKey`'s egg sizes; don't confuse the two) plus its own wire-format
    `Converter`. Reuses the same `Pen` catalog as Bird Stock/Production (confirmed
    against the frontend — all three screens list the same five pens), so
    `MortPenEntry` mirrors `ProductionPenEntry` exactly: one row per (pen, day), flat
    columns per category, no carry-forward, same-day edit lock via
    `entryDate == today`. Deliberately independent of `BirdPenRecord.mortality` even
    though both are called "mortality" — confirmed by reading both frontend screens,
    the numbers never match or sync — same reasoning as the crackGoodOpen/gcOpening
    precedent. `MortCategoryValue` holds only the two truly-live scalars per
    category (opening, opening-stock-locked; and gift qty, overwritten not
    accumulated) — everything else on the Stock Overview table is computed live
    rather than stored, to avoid any drift risk: Produced sums today's
    `MortPenEntry` rows (mirrors `ProductionService.catProdTotals`), Sales sums all
    `MortSaleEntry` rows ever for that category (mirrors
    `CrackEggService.goodSalesQty`). Catfish Feed Transfer (Green only) and Disposal
    (PM/Reject only) share one same-day-locked record, `MortCatfishDisposalState`
    (one row per day, no carry-forward) — modeled as a single combined row rather
    than the two-separate-rows sketch originally in this section, because the
    frontend's `saveCatfishDisposal()` is one atomic save covering both figures
    under one `SaveLockBar`. `MortSaleEntry`'s credit/advance are hand-typed by
    staff and persisted as-is, same as Crack Egg's `GcSaleTransaction` and unlike
    Whole Egg. Gift is a single atomic "Save Gifts" action
    (`MortalityService.saveGift`) that both overwrites the three saleable
    categories' live gift totals AND appends a permanent `MortGiftLogEntry` snapshot
    in one call — simpler than Crack Egg's gift flow, because the frontend's gift
    fields here have no independent per-keystroke live-save; they're pure form state
    until the Save button fires. Introduced a `peekTodayCatfishDisposal()`
    read-only helper distinct from the persisting `todayCatfishDisposal()`, since
    calling the latter via self-invocation from the read-only `stockOverview()` path
    would run its insert-if-missing inside a read-only transaction — worth reusing
    this pattern if a similar read/write split comes up in Feed Mill.
  - Feed Mill: the largest single module, and — like Mortality — self-contained
    (no cross-module dependency in or out, aside from its own internal Feed
    Production → Fish Feed Stock transfer, which BACKEND_PLAN.md §6 already
    describes as staying inside the module). No demo data is seeded anywhere in
    this module — ingredients, feed types, and formulations all start empty and
    are populated by the Feed Mill Manager, exactly like Bird Stock's `Pen`
    catalog starts empty. `FeedIngredient` and `FishFeedStock` both have **no day
    dimension at all** — confirmed by reading the frontend closely: unlike Bird
    Stock/Production/Mortality's per-pen-per-day rows, Ingredient Inventory and
    Fish Feed Stock behave exactly like Whole Egg's `WeCategoryValue`/Crack Egg's
    `CrackEggState` (§5.3) — single always-current rows, no reset, no
    carry-forward step anywhere in the code (only a comment saying closing
    "becomes next day's opening," never actually implemented). Formulations
    needed a genuine design decision beyond the original sketch: the frontend
    tracks unsaved-vs-saved formulation values with an ephemeral React
    `formBaseline` state that resets whenever the Feed Mill Manager switches
    which feed type they're viewing — since this backend persists every field
    write immediately (no draft concept), `FeedFormulationEntry` instead carries
    a permanent `lastSavedQtyPerTon` column alongside the live `qtyPerTon`, and
    `saveFormulation` diffs against that (then advances it) rather than trying to
    replicate the frontend's page-local baseline — more durable than the
    frontend's own approach, not just a port of it. Feed Production's
    `formulationRef` (shown as `"FM-2026-03"` in the frontend) is static
    placeholder text with zero real versioning logic behind it in the frontend
    itself — kept as an optional, never-auto-generated column rather than
    inventing a fake numbering scheme. `updateProductionEntry`'s edit path
    reconciles both ingredient usage AND Fish Feed Stock's `added` in one
    delta-based pass (old formulation × old qty subtracted, new formulation × new
    qty added), exactly like the frontend's own function of the same name — and,
    matching the frontend exactly, has **no** shortfall validation on edit, only
    on the original production run (`runProduction`/`checkProduction`). The
    Ingredient Usage by Feed Type table on the Reports tab was deliberately left
    unbuilt even though the frontend computes it from real state (not synthetic
    demo data like the rest of that tab) — it's still presentation logic that
    belongs with the rest of real Reports aggregation in Phase 4, and can be
    built there from `feed_production_log` + `feed_formulation_entries` once
    Phase 4 starts; scoping it into Phase 3 would have been inconsistent with
    every other module's Reports tab being deferred. Also introduced the same
    dense-vs-sparse fix caught during review: the Formulations tab's
    `formulation()` endpoint returns one row per ingredient in the whole catalog
    (zero-filled where no formulation row exists yet), not just the sparse rows
    that happen to exist — the frontend iterates over every ingredient there,
    unlike `checkProduction`'s requirements list, which stays sparse (only
    ingredients actually in that feed type's formulation), matching
    `Object.entries(formulation)` in the frontend.
- **Phase 4 — Reports & Relief Access** ✅ done (Relief Access ✅ done; Admin Logs already
  real since Phase 2 — see SystemLogController; Reports ✅ done — real DB aggregation,
  see §8 for the full design and the roadmap entry below for what got built):
  - Relief Access: new `relief` package — `ReliefGrant` entity (`on_leave_user_id`/
    `grantee_user_id` FKs to `User`, `reason`, `granted_by`, `granted_at`, `active`,
    `revoked_at`, `revoked_by` — matches §5.1's sketch exactly) + `ReliefGrantRepository`
    + `ReliefAccessService` + `ReliefAccessController` at
    `/api/v1/admin/relief-access`, Administrator-only throughout. `grant()` replicates
    `submitGrant`'s validation from ReliefAccessView.tsx exactly: can't cover for
    yourself, the on-leave party can't already have an active grant (error message
    names the existing grantee, same as the frontend), and — a rule the frontend
    enforces implicitly via its `eligibleUsers` filter rather than a validation
    message — neither party can be an Administrator.
  - The two real mechanics an in-memory frontend demo never had to solve: **(1)
    blocking login** — `AuthService.login` now checks
    `reliefAccessService.activeLeaveFor(user)` *after* the password already matched
    (same order as the frontend's `handleLogin`, so a wrong-password attempt never
    reveals on-leave status), throwing a new `OnLeaveException` (mapped to 423 LOCKED,
    alongside `AccountLockedException`) with the same message shape as the frontend's
    `activeLeave` check. `AuthService.refresh` enforces the same check — the frontend
    never had a refresh-token concept to compare against, but leaving it unchecked
    there would mean a grant taking effect mid-session never actually cut anyone off,
    undermining the whole point of "covering while they're away." **(2) granting the
    reliever extra module access** — this backend's `@PreAuthorize("hasRole(...)")`
    checks are role-name-based, not the frontend's flexible per-user module list, so
    an active grant has to surface as an *additional granted authority* on the
    grantee's own token. `JwtService.generateAccessToken` gained an `extraRoles`
    parameter (baked into a new `extraRoles` JWT claim); `JwtAuthenticationFilter` now
    grants `ROLE_<primary>` plus `ROLE_<extra>` for each; `AuthenticatedPrincipal`
    gained an `extraRoles` field to carry it. `AuthService` computes
    `reliefAccessService.extraRolesFor(user)` at both login and refresh time and bakes
    it into every token issued — same "recomputed at issuance, not on every request"
    staleness tradeoff `AuthenticatedPrincipal`'s own javadoc already documents for
    account deactivation, now documented there for this too. `LoginResponse` also
    returns `extraRoles` directly so Phase 5's frontend integration doesn't have to
    decode the JWT just to render the "Relief" sidebar tag.
  - Deactivating a user (`UserAdminService.setActive`, now taking the acting admin's
    username) cascades into `reliefAccessService.revokeAllForUser` — revokes every
    active grant naming that account on *either* side, matching the frontend's
    `setAccountActive` comment about keeping the access picture consistent. Not
    `@Audited` itself (the deactivation that triggered it already produces its own
    audit row).
  - Reports: new `common.reports` package (`ReportColumn`, `ReportTableResponse`,
    `ReportPeriods`) shared by all six modules plus the General Report — see §8 for the
    full design rationale (why two endpoints per module instead of a literal six-bucket
    port, the per-module column sets, and the known real-data gaps). Every module
    (`birdstock`, `production`, `wholeegg`, `crackegg`, `mortality`, `feedmill`) gained
    `GET /reports/daily?start=&end=` and `GET /reports/monthly?months=` on its existing
    controller, plus one or two new repository range-query methods and a
    `dailyReport`/`monthlyReport` pair on its existing service — no new services for the
    six modules, since this reuses the same repositories/entities Phase 3 already built.
    New top-level `reports` package (`GeneralReportService` + `GeneralReportController`
    at `/api/v1/reports/general`, Administrator-only) composes the five relevant
    modules' own `dailyReport` output rather than re-deriving revenue/output totals —
    sums specific columns out of each module's returned rows (e.g. Whole Egg's
    `revenue`/`crates`, Mortality's `salesRevenue`/`sales`) to build the "one row per
    module" shape `GeneralReportView.tsx` expects. Production's `dailyReport`/
    `monthlyReport` needed Bird Stock's real per-day closing stock as the weighted
    denominator for its `pct` column — added `birdClosingByDayInRange` (one query for
    the whole range, grouped in Java) rather than one query per day, after an
    independent review pass caught the original per-day-query version as a real,
    if minor, N+1 pattern.
- **Phase 5 — Frontend integration** (in progress): swap `FarmProvider`/`App.tsx` over
  to the API, module by module, verifying each screen still behaves exactly as it does
  today.
  - Auth now runs on httpOnly cookies rather than JSON-body tokens (§11 decision #2).
    New `AuthCookies` (`auth` package) builds/reads two cookies: `kf_access_token`
    (path `/`, httpOnly + Secure + SameSite=Strict, TTL = the existing access-token
    minutes) and `kf_refresh_token` (path `/api/v1/auth` only — no other endpoint ever
    needs it). `AuthController.login` sets both from `AuthService.login`'s result;
    `/refresh` and `/logout` take no request body anymore — they read the refresh
    cookie straight off the request and return `204 No Content` (their old
    `RefreshRequest`/`LogoutRequest` body DTOs are deleted). `LoginResponse` keeps
    `accessToken`/`refreshToken` as record components (so the controller can still read
    them to set cookies) but excludes both from the JSON body via
    `@JsonIgnoreProperties` — the whole point of httpOnly is that JS never sees the raw
    token, including in the login response itself. `JwtAuthenticationFilter` now reads
    the access token from the cookie first, falling back to `Authorization: Bearer` for
    non-browser clients (Swagger/curl/tests) — the fallback is a separate code path an
    XSS payload in the real frontend has no way to reach, so it doesn't weaken the
    cookie's protection. CSRF protection is `SameSite=Strict` on both cookies rather
    than Spring Security's token-based CSRF machinery (left disabled, same as before) —
    judged sufficient for an internal-only tool rather than adding a CSRF-token fetch
    to every mutating frontend call. New `app.security.cookie-secure` property
    (default `true`) controls the `Secure` attribute; true works out of the box in
    local dev because Chrome/Edge/recent Firefox treat `http://localhost` as a
    secure-context exception.
  - New `GET /api/v1/auth/me` (authenticated, no request body) — a gap discovered while
    designing the frontend's auth flow, not something the original sketch anticipated:
    an httpOnly cookie survives a page reload but React state doesn't, so the frontend
    needs a way to ask "is there still a valid session?" on every app load. Reuses
    `LoginResponse`'s shape (tokens null/ignored, same as everywhere else) and
    recomputes `extraRoles` fresh rather than trusting the current access token's own
    (possibly stale-until-next-refresh) claim.
  - `Role` (the `user` package enum) gained `@JsonValue`/`@JsonCreator` — another gap
    caught while designing the frontend's login flow: without it, `role`/`extraRoles`
    were serializing as the raw Java constant name (`"ADMINISTRATOR"`), not the label
    string (`"Administrator"`) the frontend's own `Role` type union expects, and there
    was no `extraRoleLabels` field to fall back on the way `roleLabel` covers `role`
    alone. Now matches `Mod`'s existing wire-format precedent exactly — the frontend
    consumes `role`/`extraRoles` directly with no translation layer needed. Several
    DTOs built before this still carry a separate `roleLabel` alongside `role`
    (`CreateUserResponse`, `LoginResponse`, etc.) — now redundant, left alone rather
    than touched as a drive-by refactor; worth a small cleanup pass in Phase 6.
  - `OpeningStockCell` (`components.tsx`) — the shared cross-module Opening-Stock
    widget every module's view renders — is now a controlled component: it accepts
    optional `locked`/`pendingRequest`/`onRequestSubmit`/`onLock`/`onBlur` props and,
    when a caller supplies them, uses those instead of the original
    `useFarm()`-backed behavior. Left undefined, it behaves exactly as before, so
    modules not yet converted (Production, Whole Egg, Crack Egg, Mortality, Feed
    Mill) needed zero changes. This is how modules are migrated one at a time without
    a big-bang rewrite of every `OpeningStockCell` call site at once.
  - `GET /api/v1/opening-stock/lock` gained a second response field —
    `LockStatusResponse.pendingRequestByMe` — because `GET /opening-stock/requests`
    is Administrator-only, so a manager had no way to find out whether their own
    unlock request for a field was still pending, including after a page reload.
    Backed by a new `existsByModuleAndScopeAndRequestedBy_IdAndStatus` repository
    method and `OpeningStockRequestService.hasPendingRequestByUser`. Scoped to "by
    me" specifically, not "any pending request for this scope."
  - Bird Stock (`BirdStockView.tsx`) is the first module fully swapped onto the real
    API: `GET /records/today` on load (plus a per-pen `GET /opening-stock/lock` to
    seed `pendingRequestByMe`), `PATCH /records/{penId}` on field blur,
    `POST /records/{penId}/lock-opening` for the manager's "Done — Lock" action,
    `POST /pens` / `DELETE /pens/{id}` for add/remove. The old "Save Record" button
    and save/unlock toggle are gone — the backend's per-record `editable` flag (true
    only for today's row) already collapses that into one rule (see
    `BirdStockService`'s javadoc), so every field now auto-saves on blur instead.
    `NumInput` gained an optional `onBlur` prop for this (unused by not-yet-converted
    modules). One known transitional gap: `ProductionView`/`WholeEggView` still read
    bird totals from the in-memory `FarmProvider` store's `pens`, which Bird Stock no
    longer writes to — their cross-module bird-closing figures will be stale until
    Production is converted (next, Phase 5 task list) to read the real
    `GET /bird-stock/records/today` total instead.
  - Production (`ProductionView.tsx`) is the second module converted. Its backend
    already composed Whole Egg's and Crack Egg's live figures (§6's bidirectional
    `@Lazy`-injected cross-module reads in `ProductionService`) before this — the
    frontend swap just wires the UI to what was already there. `GET
    /pen-entries/today` + `GET /day-state/today` load on mount; per-pen production
    cells `PATCH /pen-entries/{penId}` (one category at a time — matches the
    frontend's per-cell `onChange`); category Opening Stock cells and the six Crack
    Egg tab fields both flow through `PATCH /day-state/cat-opening` and `PATCH
    /day-state/crack-fields` respectively, on blur. Same autosave-on-blur model as
    Bird Stock, same reason (no more save/unlock toggle — `editable` is the only
    rule). Two more known transitional gaps, in the same spirit as Bird Stock's:
    (1) `WholeEggView`/`CrackEggView` still read Production's numbers from the
    frozen in-memory store's `penProd`/`catOpening`/etc. (Production no longer
    writes there), so their auto-received "Egg Production"/"Crack Received"
    figures are stale until they're converted; (2) Production's own "auto"
    columns (Crack Use, Total Sales, Gift from Whole Egg; Good Crack Gift/Sales
    from Crack Egg) will read zero from the real backend until those two modules
    are converted and start writing real transactions there — the wiring is
    already correct, there's just no real data behind it yet.
  - Whole Egg (`WholeEggView.tsx`, ~2,200 lines — the largest module in this
    migration) is the third converted. Stock Overview's Opening Stock/Price cells
    autosave on blur exactly like Bird Stock/Production; Sales Crack and Gift keep
    their original explicit "Save & Transfer" buttons — those are real commit
    actions (`PUT /stock/sales-crack` / `PUT /stock/gift`, whole-array-replace), not
    per-field autosave, so a button stays appropriate there. New Sale, the
    Customers directory, and a customer's Purchase History are now all backed by
    real paginated endpoints instead of the in-memory store's flat arrays.
    Three small, justified backend extensions came out of building the real UI
    (none were scope creep — each closes a gap the existing API genuinely didn't
    cover):
    (1) `LockStatusResponse.pendingRequestByMe`-style gap didn't recur here (that
    was Bird Stock's), but `CustomerResponse` gained `visitCount`/`totalCrates`/
    `totalRevenue`/`lastPurchaseAt`, computed in `WholeEggService.toCustomerResponse`
    via two new `WeSaleLineItemRepository` aggregate queries and two new
    `WeSaleTransactionRepository` count/latest-txn queries — needed once the
    Customers directory became a real server-paginated table rather than something
    that could recompute these client-side over an in-memory array. Follows the
    same year-restriction rule already established for `customerHistory`/
    `allTransactions`: `isAdmin` sees all-time, a manager sees the current year
    only.
    (2) New `GET /customers/outstanding-summary` (`OutstandingBalanceResponse`,
    a `WeSaleTransactionRepository` correlated-subquery aggregate over each
    customer's single latest transaction) — the Sales Transactions table's footer
    used to sum farm-wide outstanding credit/advance across every loaded row,
    which real pagination makes impossible client-side; this one endpoint replaces
    that math exactly. Per-category qty/revenue footer totals were simplified to
    "this page" only — not worth a further aggregate endpoint.
    (3) `listCustomers`/`searchCustomers`/`recentCustomers` all gained an
    `isAdmin` parameter (threaded from `AuthenticatedPrincipal` in the controller)
    for the same reason as (1).
    Two things deliberately left alone: `WeSaleTxnType` has no `@JsonValue`
    (unlike `Mod`/`Role`/`CatKey`/`PaymentMethod`), so it serializes as the raw
    Java constant name (`"SALE"`/`"PAYMENT"`/`"OPENING"`); the frontend was
    written to consume that wire format directly rather than treating it as a bug
    to fix, since nothing else depends on the label form. And gift `recipient`/
    `authorizer` fields still have no backend column — confirmed these were never
    actually persisted in the pre-migration app either (pure local UI state), so
    keeping them as local-only React state in the rewrite is not a regression.
    `components.tsx`'s `RowEditButton` (date-stamp-prefix check, `isEditableToday`)
    turned out not to fit here — `WeSaleTransactionResponse` already carries a
    live `editable` boolean computed server-side (`WholeEggService.isEditable`,
    same-day-only), so `WholeEggView.tsx` has its own small local
    `EditOrLockedButton` driven directly off that flag instead of forcing a real
    boolean through a component built for a date-string prefix match. The edit
    affordance is also intentionally restricted to `SALE`-type transactions only
    — `updateTransaction` technically allows editing a `PAYMENT` row's method/
    amount too, but that would need its own non-items edit form (closer to
    `RecordPaymentModal` than the sales-items grid `EditSaleTxnModal` provides);
    left as a known gap rather than building a second edit modal for a rare case.
    One known transitional gap, same pattern as Bird Stock/Production: Crack Egg's
    "auto-received" figures (Good Crack Gift/Sales feeding back into Production)
    will read stale/zero until Crack Egg (next, Phase 5 task list) is also
    converted and starts writing real transactions.
  - Crack Egg (`CrackEggView.tsx`) is the fourth converted, and needed zero
    backend changes — `CrackEggService`/`CrackEggController` were already
    complete from Phase 3, including reading Good/Rough Crack's Production &
    Received figures live off `ProductionService.getTodayDayState()` and
    exposing a per-record `editable` boolean on both `GcSaleResponse` and
    `GiftLogEntryResponse`, the same pattern Whole Egg's `WeSaleTransactionResponse`
    established. The frontend swap was purely wiring: `GET /stock` on load
    (Good + Rough Crack in one call) plus the two opening-stock lock checks;
    Good Crack Opening Stock and Selling Price autosave on blur through
    `OpeningStockCell`/`NumInput` exactly like Whole Egg's Stock Overview. Good
    Crack Sales and the Gift log both kept their original inline-row-edit
    table UI (not a modal, unlike Whole Egg) — `PATCH /sales/{id}` and
    `PATCH /gift/log/{id}` only ever touch the fields `UpdateGcSaleRequest`/
    `UpdateGiftLogEntryRequest` expose (customer/state/qty/price/credit/advance
    for a sale; qty/recipient/authorizer for a gift entry — payment method,
    bank and amounts stay fixed once a sale is made, matching the original
    `GcSaleDraft` type exactly). Both tables reuse the same local
    `EditOrLockedButton` pattern Whole Egg introduced (a live `editable`
    boolean in place of `RowEditButton`'s date-stamp-prefix check).
    Credit/Advance here are still hand-typed by staff on the sale form, not
    auto-derived — `CreateGcSaleRequest`/`GcSaleTransaction` never computed
    them, so this is a real behavioral difference from Whole Egg's sales
    (which do auto-derive credit/advance from amount owed vs. paid) that was
    already present before this migration and is preserved as-is, not
    "fixed" to match Whole Egg.
    The Gift tab's three fields (qty/recipient/authorizer) now autosave to
    `CrackEggState` on blur (`PATCH /gift/qty`, `/gift/recipient`,
    `/gift/authorizer`), and clicking "Save Gift" flushes all three first,
    then calls `POST /gift/log` — which snapshots whatever `CrackEggState`
    currently holds into a permanent log row (`saveGiftSnapshot()` reads live
    state rather than taking new values in its request body), so the
    frontend has to guarantee the autosaves have landed before asking for the
    snapshot rather than relying on eventual consistency. Feed Mill Usage
    keeps its original explicit "Save" button (not autosave) because it's
    hard-validated server-side against available Rough Crack stock
    (`setRcFeedMill` throws `BadRequestException` over the limit) — a commit
    action, not a per-keystroke save, same reasoning as Whole Egg's Sales
    Crack & Gift buttons.
    No known transitional gaps remain from this conversion — Crack Egg was
    the last module reading stale in-memory data from any of Bird Stock,
    Production, or Whole Egg (all three are now live), and nothing downstream
    reads from Crack Egg's own old in-memory store except Feed Mill (next,
    Phase 5 task list), which will pick up real Feed Mill Usage figures once
    it's converted.
  - Mortality (`MortalityView.tsx`) is the fifth converted, and — like Crack
    Egg — needed zero backend changes; `MortalityService`/`MortalityController`
    were already complete from Phase 3. Mortality is the one module with no
    cross-module feed in or out at all (BACKEND_PLAN.md §6 never lists it),
    so this conversion carries no transitional staleness in either
    direction. Two real UX changes came out of following the backend's
    actual data model rather than the original mock's shape:
    (1) Pen Mortality Entry (`GET /pens/today` + `PATCH /pens/{penId}`)
    dropped its single-day `SaveLockBar`/manual "Save Mortality Record"
    button in favor of per-cell autosave-on-blur, matching Bird Stock and
    Production — the backend already exposes a live `editable` boolean per
    pen-per-day row (`MortalityService.isEditable`), so there's nothing left
    for a manual lock toggle to do that the flag doesn't already cover.
    (2) Catfish & Disposal (`GET /catfish-disposal/today` +
    `PATCH /catfish-disposal`) converted the same way — one state object per
    day with its own `editable` flag, autosaved on blur rather than behind
    an explicit Save/Edit toggle. Gift is the one exception that correctly
    stayed a single explicit "Save Gifts" button: `SaveGiftRequest` is one
    atomic backend call that commits Good/Dry/Runt gift totals and appends a
    permanent log row together, with no per-field autosave endpoint the way
    Crack Egg's gift fields have — so the original all-at-once UX was
    already the right shape for this backend and needed no redesign, just
    real wiring (`POST /gift`, `PATCH /gift/{id}` for the log's inline edit,
    `GET /gift` paginated).
    Sales Transactions (`GcSalesTransactionsTable`-style inline-row-edit,
    reused from the Crack Egg pattern) now carries an extra Category column
    (`MortCat` — Good/Dry/Runt/Green/PM-Reject, `@JsonValue`'d to match the
    frontend's string union exactly) but is otherwise identical in shape:
    `UpdateMortSaleRequest` only allows correcting customer/state/qty/price/
    credit/advance, never category or payment method, once a sale is made.
    Credit/Advance stay hand-typed by staff on the sale form (never
    auto-derived), same as Crack Egg and unlike Whole Egg. The Dashboard
    tab's Sales Revenue card sums a single bounded fetch
    (`GET /sales?size=500`) rather than adding a dedicated revenue-aggregate
    endpoint — same judgment call as Whole Egg's bounded customer-history
    fetch, comfortably covers realistic daily volume without a new backend
    call.
  - Feed Mill (`FeedMillView.tsx`) is the sixth converted and — the largest
    module by entity count so far — needed zero backend changes;
    `FeedMillController`/`FeedMillService` were already complete from Phase 3.
    Ingredient Inventory and Fish Feed Stock have no day dimension at all
    (pure running totals, same as Whole Egg's `WeCategoryValue`), so Opening
    Stock, Qty Added, Min Level and Unit all autosave on blur/change through
    `OpeningStockCell`/`NumInput` exactly like every other module's stock
    tables; ingredient names (not numeric IDs) are the path key for these
    endpoints, so the frontend URL-encodes each name (`Premix (Layer)` etc.)
    when building the request path. `low` and `closing` are read straight off
    `FeedIngredientResponse` rather than recomputed client-side. Ingredients,
    feed types, and formulations all start genuinely empty per the backend's
    "no demo data seeded" design (`FeedMillService`'s class javadoc) — the
    original's large hardcoded seed arrays (7 ingredients, 7 feed types,
    nested formulation records) were dropped entirely in favor of real
    `GET`-driven empty-state rendering plus the existing "Add Ingredient"/
    "Add Feed Type" inline forms, now wired to `POST /ingredients`/
    `POST /feed-types`.
    Formulation editing kept its original two-phase shape but the diffing
    moved server-side: each ingredient's Qty per Ton autosaves live on blur
    via `PATCH .../formulation`, and the "Save Formulation" button separately
    calls `POST .../formulation/save`, which now does its own diff against
    each entry's `lastSavedQtyPerTon` and only writes a
    `FeedFormulationHistoryGroup` (+ child items) for ingredients that
    actually changed — the frontend no longer tracks a baseline snapshot
    itself. Total kg / status (ok/under/over) are recomputed locally after
    each edit for instant feedback using the same `FORMULATION_TARGET_KG`
    (1000) / `FORMULATION_TOLERANCE_KG` (5) constants the backend uses, then
    resynced from the server's authoritative `FormulationResponse` after
    every full reload — this mirrors the same "lightweight client mirror,
    server resync on refetch" pattern used for Production's local state.
    Feed Production's client-side `requirements`/`canProduce` computation
    was replaced entirely by a debounced live call to
    `GET /production/check?feedTypeId&qtyTons` as the user edits qty/feed
    type; `runProduction`/`updateProductionEntry` already handle fish-feed
    auto-transfer and ingredient-usage reconciliation fully server-side, so
    the frontend no longer replicates `FISH_FEED_MAP`/`FISH_FEED_TARGETS`
    logic at all — `POST /production` and `PATCH /production/{id}` just
    refresh Ingredient Inventory, Fish Feed Stock and Production Summary
    afterward. Fish Feed Stock's `OpeningStockCell` scope convention is
    `fish:${type}` (e.g. `fish:Fish Starter`), matching
    `FeedMillService.setFishFeedOpening`'s lock-service scope key exactly.
    Fish Feed Collection's `CollectionRecordsTable` is reused verbatim
    (server-paginated, inline-row-edit) in both the Fish Feed Collection tab
    and the Reports tab, same as the original; `UpdateCollectionRequest`
    allows every field to be corrected in place with no restricted-field
    carve-out, unlike every other module's sale/entry edit DTOs.
    One real judgment call: there is no backend aggregate for "Ingredient
    Usage by Feed Type" (usage broken out per ingredient × feed type, not
    just a single daily total), so the Reports tab does a bounded fetch
    (`GET /production?size=1000`) plus one formulation fetch per feed type
    (parallel), then replays the same qty-per-ton × tons-produced math the
    backend's own `reportRow` uses internally — same judgment call as
    Mortality's bounded dashboard revenue fetch, comfortably covers
    realistic production volume without a new endpoint. `formulationRef` on
    `ProductionLogResponse` is rendered as `—` when absent — nothing in
    `FeedMillService.runProduction` currently sets it, so it is expected to
    read empty for the foreseeable future; not treated as a bug to fix here.
    No known transitional gaps remain — Feed Mill was the last module reading
    stale in-memory Crack Egg Feed Mill Usage figures, and all six Phase 5
    stock/production modules are now fully live.
  - Relief Access + Admin (Users/Logs) is the seventh conversion and closes
    out a gap that had quietly persisted since auth moved to the real
    backend: `AdminView.tsx`'s User Management and Relief Access tabs, and
    `ReliefAccessView.tsx`'s `ReliefTab`, were still reading/writing a local
    in-memory registry in `App.tsx` (`registeredUsers`/`reliefGrants`) that
    had no relationship to real accounts at all — creating a "user" there
    never actually let anyone sign in, and granting "relief" there never
    touched `extraRoles` (which the server already computes correctly at
    login/refresh via `ReliefAccessService.extraRolesFor`). `UserAdminController`/
    `ReliefAccessController`/`SystemLogController` were already complete
    from earlier phases, so this was purely a frontend wiring gap, not a
    backend one — no backend changes needed.
    User Management now calls `GET/POST /admin/users`, `PUT /admin/users/{id}`,
    `PATCH /admin/users/{id}/active`, `POST /admin/users/{id}/reset-password`
    directly; the Create User form dropped its Email and Password fields
    entirely (`CreateUserRequest` only ever took `fullName`/`username`/`role`
    — there's no email column on `User`, and passwords are always server-
    generated, never operator-chosen). Both Create User and Reset Password
    now surface the real one-time `generatedPassword` in the same
    "temporary password" panel the original mock used only for resets — the
    generated value is never retrievable again after that panel closes,
    matching `CreateUserResponse`/`ResetPasswordResponse`'s one-time-reveal
    contract exactly. There's no search endpoint on `UserAdminController`,
    so the Users table does one bounded fetch (`size=200`) and filters/
    paginates client-side — same judgment call as every other "no dedicated
    query endpoint" case in this migration, comfortably covers realistic
    headcount for a single farm.
    `ReliefTab` is now fully self-contained (no props) — it fetches
    `GET /admin/relief-access/eligible-users` + `/active` itself and posts
    grants/revokes straight to `POST /admin/relief-access` /
    `POST /admin/relief-access/{id}/revoke`; `AdminView` no longer threads
    `registeredUsers`/`grants`/`onGrant`/`onRevoke`/`onSetAccountActive`/
    `onCreateUser`/`onUpdateUser` down to it at all, and `App.tsx` dropped
    that entire local registry (~70 lines) along with the now-unused
    `SYS_USERS`/`RegisteredUser`/`ReliefGrant` imports. Grant/revoke
    validation (relieving officer can't cover themselves, on-leave user
    can't already have an active grant) is checked client-side first for
    instant feedback, exactly mirroring `ReliefAccessService.grant`'s own
    server-side checks — both layers agree by construction since the
    frontend copy was written directly from reading the service method.
    Security & Logs replaced its three hardcoded arrays
    (`ACCESS_LOG`/`ACTIVITY_LOG`/`AUDIT`) with one `SystemLogTable`
    component reused for all three sub-tabs, calling
    `GET /admin/logs/{ACCESS|ACTIVITY|AUDIT}` (server-paginated) — matches
    `SystemLogController`'s single-table-discriminated-by-`LogType` design
    exactly, so no per-tab special-casing was needed beyond which columns
    to show.
    One real functional gap got fixed as a side effect of this conversion,
    not just a data-source swap: the Opening Stock Requests tab was reading
    `farm.openingRequests` from `store.tsx`'s `FarmProvider`, a second
    local-only array that nothing had written to since every module's
    `OpeningStockCell` started posting straight to the real
    `POST /opening-stock/requests` endpoint (Phase 5, `OpeningStockCell`
    rewrite) — meaning every pending request submitted since then was
    invisible to the admin, and approving one there
    (`farm.resolveOpeningRequest`) never actually called
    `OpeningStockRequestService.resolve`, so the real lock never unlocked.
    The tab now calls `GET /opening-stock/requests` (bounded fetch,
    `size=200`, split client-side into pending vs. resolved like the
    original array was) and `POST /opening-stock/requests/{id}/resolve`
    directly — this is the only path by which `lockService.unlock` ever
    runs, so this was blocking every module's "request to re-edit Opening
    Stock" flow from ever actually resolving, not just a cosmetic staleness
    issue.
    Roles & Permissions and System Configuration are unchanged — both are
    genuinely reference/local-only (a static permissions matrix and a farm-
    info/pens/categories editor with no backend model behind them yet), out
    of scope for this pass.
  - Reports tabs (eighth conversion, all six modules + General Report) is
    the largest single rewrite of this migration by design-intent, not by
    line count: §8's design doc was explicit that this was never meant to
    be a literal port of `ReportsPanel.tsx`'s original "6 static period
    buckets" shape, and the backend was built accordingly back in Phase 4 —
    every module exposes exactly `GET {base}/reports/daily?start&end` and
    `GET {base}/reports/monthly?months=N`, nothing else. `ReportsPanel.tsx`
    itself was rewritten around that: Week and any custom date range now
    call `/reports/daily`; Quarter (`months=3`) / Half-Year (`months=6`) /
    Year (`months=12`) call `/reports/monthly`; Today reuses whatever real
    per-entity "today" data the calling module already has loaded from its
    own Phase 3 endpoint (`todayColumns`/`todayRows` props) rather than a
    new endpoint, exactly as designed. Every stat card is now derived
    client-side from row data via `rangeStats`/`flatStats` (generalized
    versions of the original `aggregateStats` helper, applied uniformly
    instead of only for custom ranges) — the backend has no bespoke stat-
    card concept, so the four hand-authored "editorial" stats each period
    used to carry (e.g. Bird Stock's "Mortality Rate %"/"Net Change vs
    yesterday") are gone, replaced by plain "Total {column}" sums; a real,
    documented simplification, not an oversight. The trend chart, custom
    date-range filter, and Excel/PDF/Word export all stayed entirely
    frontend-side and needed no changes beyond reading from the new data
    shape. A new `formatCell?: (key, value) => string | number` prop was
    added (not in the original design doc) because the backend's numeric
    report columns (e.g. Whole Egg's `revenue`/`avg`, Crack Egg's
    `goodRevenue`, Mortality's `salesRevenue`) come back as plain numbers,
    not "₦"-prefixed strings — each module supplies a small formatter
    (`weFormatCell`, `ceFormatCell`, `mortFormatCell`) so currency columns
    still render and sum correctly instead of pushing that formatting
    into the shared component or the backend.
    Per §8's real-data-gap note, several modules' aggregate report columns
    are narrower than their old synthetic data implied — Crack Egg dropped
    `roughToFeed`/`closing` (no historical log, only a live running total),
    Feed Mill dropped `alerts` (low-stock is live-only) — the frontend
    column constants for each module now match the backend's actual
    `REPORT_COLUMNS` exactly rather than the old wider synthetic set.
    Where no "Today" per-entity endpoint fetch already existed in a
    module's loaded state (Whole Egg, Crack Egg, Mortality), each got a
    small bounded fetch (`size=200`, filtered client-side to today's date)
    added specifically for the Reports tab, following the same "bounded
    fetch over a new aggregate endpoint" judgment call used throughout
    Phase 5; Feed Mill's Today rows instead reuse the `usageProduction`/
    `usageFormulations` state its Ingredient Usage report already fetches.
    General Report (`GeneralReportView.tsx`) does **not** use `ReportsPanel`
    at all — per §8 its rows are one-per-module for a date range, not
    one-per-day/month, which doesn't fit the drill-down model the rest of
    `ReportsPanel` is built around. It's a small bespoke period-tab +
    table component instead, computing the same date range semantics
    (trailing 3/6/12 months for Quarter/Half-Year/Year) client-side and
    calling the single `GET /api/v1/reports/general?start&end` endpoint.
    Flagged, not fixed, while doing this: `GeneralReportController` is
    `@PreAuthorize("hasRole('ADMINISTRATOR')")`-only, but `shared.ts`'s
    `ACCESS` matrix routes `Managing Director` into `/general-report` too
    (`canExport={false}` and the "Managing Director" framing in both the
    original frontend and §8's design doc both imply that role should be
    able to view it) — a Managing Director account will get a real 403 from
    this endpoint today. This is a role-policy decision (whether to widen
    the `@PreAuthorize`, or narrow `ACCESS`), not something to silently
    patch as a drive-by during a frontend wiring pass — worth resolving in
    Phase 6's `@PreAuthorize`-vs-§4 audit.
- **Phase 5: Pagination wiring + final review**: audited every remaining
    `usePagination(` call site (the old client-side pagination hook) across
    `src/app` — three left, all a deliberate "bounded fetch + client-side
    pagination/filter" pattern already used and documented throughout this
    migration, not a missed server-pagination opportunity: `WholeEggView`'s
    `CustomerDetail` over `periodTxns` (a client-filtered slice of an
    already-bounded `size=1000` customer-history fetch), and `AdminView`'s
    Users table and Opening Stock Requests history, both over `size=200`
    bounded fetches. Left as-is. Separately, found and removed a block of
    genuinely dead code left over from the module-by-module API migration:
    `OpeningStockCell` (`components.tsx`) had carried a dual-mode design
    since early Phase 5 — a live-backend path (when the caller passes
    `locked`/`pendingRequest`/`onRequestSubmit`/`onLock`) and a fallback to
    the original in-memory `useFarm()` store, explicitly so modules could be
    migrated one at a time without breaking the ones still pending. Now that
    all six modules (Bird Stock, Production, Whole Egg, Crack Egg,
    Mortality, Feed Mill) pass those props at every call site, the fallback
    was unreachable. Removed it, made the four props required, and dropped
    the `useFarm` import — which in turn meant nothing in the whole
    frontend called `useFarm()` or read from `FarmContext` any more (the
    `<FarmProvider>` wrapper in `App.tsx` was only there to supply that
    context to routes beneath it). Deleted `store.tsx` outright (the
    in-memory `pens`/`penProd`/`weTransactions`/`gcTransactions`/opening-
    lock state that predated the backend, ~400 lines) and the `<FarmProvider>`
    wrapper, plus three smaller orphans this uncovered: `shared.ts`'s
    `OpeningStockRequest` type and `openingLockKey()` helper (only consumed
    by the deleted store), `shared.ts`'s `SYS_USERS` mock array and
    `ReliefGrant` type (superseded by `AdminView`/`ReliefAccessView`'s real
    API wiring, no longer imported anywhere), and `components.tsx`'s
    `RegisteredUser` type (same). `npx vite build` succeeded cleanly before
    and after, with no change in output size — confirming this was already
    dead, unreferenced code rather than something silently load-bearing.
- **Phase 6: `@PreAuthorize`-vs-§4 audit**: read every `@RestController` in the
    backend (14 total) and compared its class-level and method-level
    `@PreAuthorize` against §4's access map. Found and fixed exactly one
    mismatch — `GeneralReportController` was `hasRole('ADMINISTRATOR')`-only,
    but §4 explicitly gives Managing Director general-report as their one
    module, so that role was getting a real 403 on the only thing it exists
    to view. Widened to `hasAnyRole('ADMINISTRATOR', 'MANAGING_DIRECTOR')`.
    Everything else checked out: every per-module controller
    (BirdStock/Production/WholeEgg/CrackEgg/Mortality/FeedMill) follows the
    same intentional, already-documented split — class-level
    `hasAnyRole('ADMINISTRATOR', '<ROLE>_MANAGER')` grants both roles read
    access, then write endpoints (`POST`/`PUT`/`PATCH`) carry their own
    narrower `hasRole('<ROLE>_MANAGER')` so Administrators can view but
    never directly mutate module data — consistent with `OpeningStockCell`'s
    "Administrators never edit... directly, in any module" rule. Admin-only
    surfaces (`UserAdminController`, `SystemLogController`,
    `SecuritySettingsController`, `ReliefAccessController`, and
    `OpeningStockController`'s list/resolve endpoints) are all correctly
    `hasRole('ADMINISTRATOR')`-only, matching `admin` being Administrator-
    exclusive in §4. `OpeningStockController`'s shared GET-lock-status/
    POST-request endpoints are intentionally open to any authenticated user
    (fine-grained module access is enforced by the calling module's own
    controller, per its own javadoc). `AuthController`'s login/refresh/
    logout are correctly unauthenticated (public), and `/me`/
    `/change-password` require only a valid session, no specific role.
- **Phase 6 — Security & hardening pass**: completed the remaining five items.
  - **CORS review**: origins were hardcoded to the two Vite dev-server addresses
    with a comment flagging "tighten/parameterize before deploying anywhere
    real." Parameterized: `AppSecurityProperties.corsAllowedOrigins` (new,
    bound from `app.security.cors-allowed-origins`, comma-separated) feeds
    `SecurityConfig`'s CORS filter, read from `APP_CORS_ALLOWED_ORIGINS`
    (falls back to the same two dev origins so local checkouts still work
    with zero config) — same pattern as `API_SHARED_KEY`/`JWT_SECRET`.
  - **Actuator lockdown**: already correct, no change needed. `application.yaml`
    exposes only `health` (`management.endpoints.web.exposure.include:
    health`), and `RateLimitFilter`/`ApiKeyFilter` both run as servlet filters
    ahead of Spring Security's `authorizeHttpRequests`, so even the
    `permitAll()`'d `/actuator/health` still has to clear the API-key gate
    first — nothing actuator-related is reachable by an untrusted client.
  - **Secrets audit**: found a real one. `application.yaml`'s MySQL
    `datasource.username`/`password` were hardcoded (`root` /
    `"Emmanuel@123"`) rather than read from an env var like every other
    secret in that file — and that file is committed, so the real password
    has been sitting in git history since the `Initial project` /
    `Connected to DB.` commits. Parameterized to
    `${DB_USERNAME:root}`/`${DB_PASSWORD:changeme}` (placeholder fallback,
    not the real value) matching the `API_SHARED_KEY`/`JWT_SECRET` pattern.
    **Not done here, needs a human decision**: rotate the actual MySQL
    password (changing the file doesn't remove it from git history), and
    decide whether that history needs scrubbing given how the repo is
    hosted/shared.
  - **Dependency check**: no pinned-version CVEs to act on. Cross-checked the
    April 23, 2026 Spring Boot 8-CVE batch (headlined by CVE-2026-40976,
    critical, an actuator-vs-health module-split bug making the whole default
    filter chain unauthenticated) against this app's actual versions/config:
    every one of the 8 is either scoped to Spring Boot 4.0.0–4.0.5 specifically
    (this app is on 4.1.1, and separately isn't relying on the *default*
    filter chain at all — `SecurityConfig` defines its own), or gated on a
    feature/config this app doesn't use (Elasticsearch/RabbitMQ/Cassandra SSL
    bundles, `server.servlet.session.persistent=true`, `${random.value}`
    placeholders, `spring-boot-devtools` — none present). `jjwt` 0.12.6 (used
    for JWT signing) has no known CVEs against it directly; the one JJWT CVE
    that comes up in searches (CVE-2024-31033) is against 0.11.5, already
    fixed by being on 0.12.x. `mysql-connector-j` is intentionally unpinned,
    inheriting whatever version the `spring-boot-starter-parent:4.1.1` BOM
    manages, rather than a version chosen and left to go stale here. Worth
    re-checking whenever bumping the Spring Boot parent version, not a
    one-time clearance.
  - **Rate limiting**: added `RateLimitFilter` (new,
    `security/RateLimitFilter.java`) — in-memory, per-IP, fixed 60-second
    windows, no new Maven dependency (a library like Bucket4j would need a
    dependency this offline-during-development environment can't resolve or
    compile-verify). Two limits: a strict one (10/min/IP) on
    `POST /api/v1/auth/login` specifically, and a generous general-abuse
    backstop (120/min/IP) on everything else. Why login needs its own limit
    even though `AuthService` already has per-account lockout
    (`SecuritySettings.lockoutAttempts`, 5 attempts → 15-minute lock): that
    lockout only engages once a *known* username has racked up failed
    attempts — an attacker cycling through usernames, or just hammering with
    one that doesn't exist, hits no lockout there at all today, so the two
    protections are complementary, not redundant. Registered first in the
    filter chain, ahead of `ApiKeyFilter`, so a flood is rejected before the
    API-key check, JWT parsing, or the database are touched. Single-instance-
    only by design (in-memory map, no shared store) — would need Redis or
    similar if this ever runs as more than one instance.

---

## 11. Open Decisions (need your input before or during the relevant phase)

1. ~~Reports~~ — **decided**: real DB aggregation, built during Phase 4 (§8), not a
   literal port of the frontend's synthetic six-bucket shape.
2. ~~Token storage~~ — **decided**: httpOnly cookie, set by the backend, over
   localStorage. Better XSS protection was judged worth the extra setup (CORS
   credentials, a cookie-based auth flow instead of an `Authorization` header,
   SameSite=Strict in place of token-based CSRF protection — see Phase 5's roadmap
   entry for exactly what changed).
3. ~~Crack Egg customer~~ — **decided**: stays free-text, no customer directory or
   running-balance treatment. Whole Egg keeps that feature exclusively.
4. ~~Default password delivery~~ — **decided**: the system auto-generates a random
   default password on user creation and returns it once in the create-user response
   for the admin to relay to the new user. Never stored or retrievable in plain text
   again after that.
