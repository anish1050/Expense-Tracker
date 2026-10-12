# Spent - Design Spec

Date: 2026-10-12
Status: approved in chat, pending written review

## 1. Goal

A minimal, offline-first Android expense tracker ("Spent") that captures
transactions automatically from payment-app notifications, bank SMS alerts and
gallery screenshots, with zero upfront cost to launch. The UI follows the
attached three-screen mockup (onboarding, home with balance + chart + list,
add-expense keypad) plus a report screen with a donut split, budgets and a
saving tip.

## 2. Non-negotiable constraints

| Constraint | Value |
|---|---|
| Client | Android only, Kotlin, Jetpack Compose. iOS later in Swift, so the backend API and sync format are platform-neutral JSON. |
| Local store | SQLite via Room. All transactions live on-device. |
| Backend | Hono 4 + TypeScript on Node 24, MongoDB via native driver, deployed on Vercel Hobby. Same conventions as foodxp-b2c-service. |
| Cost | Zero paid services. Vercel Hobby, MongoDB Atlas M0, GitHub Actions free tier, Google ML Kit on-device. Only unavoidable cost: Google Play one-time USD 25 developer fee. |
| Privacy | Raw SMS, notification and image text never leaves the phone. Backup and sync carry only client-side-encrypted rows. |
| Bank linking | Account Aggregator (RBI), Plaid and similar are out of scope for phase 1 (paid). |
| SMS capture | Google Play forbids `READ_SMS`/`RECEIVE_SMS` for apps that are not the default SMS handler, no expense-tracker exception, legacy exception only for APKs published before 2019. So: Gradle flavor `play` = notification + gallery + manual; flavor `foss` = adds SMS, distributed via GitHub Releases and F-Droid. One codebase, one manifest overlay, same `applicationId`, so a user ever has one install. |
| Repo | Monorepo at github.com/anish1050/Expense-Tracker: `android/`, `service/`, `shared/`, `docs/`. Branch flow `feature -> master -> staging -> production`. |
| DB name | Local dev Mongo: `mongodb://localhost:27017/`, database `spent` (from `MONGODB_DBNAME`). |
| Renameable brand | The name "Spent" may change. It appears only in: Android `app_name` string resource, backend `package.json` `name`, `MONGODB_DBNAME`, and docs. Android `applicationId` and Kotlin package are brand-neutral `com.anish1050.ledger` because Play makes `applicationId` permanent. No class, folder, collection or route is named after the brand. |

## 3. Architecture

```
+---------------------------- Android (Kotlin/Compose) ---------------------------+
| Capture sources                  Parser           Store            UI           |
|  NotificationListenerService -+                                                 |
|  SMS BroadcastReceiver -------+-> RuleEngine -> TransactionRepo -> Room -> Compose
|  Gallery OCR (ML Kit) --------+   (shared JSON   (dedupe, review     ^           |
|  Manual keypad -------------------> rules)        state)             |           |
|                                                                      |           |
|  SyncWorker (WorkManager) <-- encrypts rows with user key -----------+           |
+-----------------+---------------------------------------------------------------+
                  | HTTPS, JWT (jose HS256)
+-----------------v------------ service (Hono + Mongo) ---------------------------+
| POST   /api/auth/google   exchange Google ID token -> app JWT                   |
| POST   /api/sync/push     upsert opaque encrypted rows (LWW by version)         |
| GET    /api/sync/pull     rows changed since cursor                             |
| GET    /api/rules         versioned parser ruleset (public, cached)             |
| DELETE /api/me            Play-policy account + data deletion                   |
+---------------------------------------------------------------------------------+
```

Server is dumb storage. It cannot read any transaction. That is what keeps
compliance cheap and the iOS port simple.

## 4. Android app

### 4.1 Stack

- Kotlin 2.1, Compose BOM 2025.x, Material 3, Navigation Compose.
- Room 2.7 with KSP. kotlinx.serialization for JSON. OkHttp for HTTP.
- Manual dependency wiring in one `AppContainer` (no Hilt; fewer concepts, faster builds).
- WorkManager for background sync. ML Kit Text Recognition v2 (on-device, free).
- Credential Manager for Google Sign-In.
- minSdk 26, targetSdk 35.

### 4.2 Modules and folders (single Gradle module `app`, package `com.anish1050.ledger`)

```
app/src/main/java/com/anish1050/ledger/
  LedgerApp.kt           Application: builds AppContainer
  AppContainer.kt        wires db, repos, parser, api client
  data/
    db/                  Room: LedgerDatabase, entities, DAOs
    repo/                TransactionRepository, CategoryRepository, BudgetRepository
    api/                 LedgerApi (OkHttp + serialization), AuthStore (EncryptedSharedPreferences)
    sync/                SyncEngine, RowCrypto, SyncWorker
  capture/
    notification/        TransactionNotificationListener
    sms/                 BankSmsReceiver            (foss flavor source set only)
    gallery/             ReceiptOcrScanner
    parser/              RuleEngine, RuleSet models, RuleStore (bundled + remote update)
  insight/
    BudgetTip.kt         pure function: spend vs budget -> tip text
  widget/
    QuickAddWidget.kt    Glance 1x1: tap opens AddExpenseScreen
    BalanceWidget.kt     Glance 2x2: month balance + month budget progress
    WidgetRefresh.kt     updateAll() called by TransactionRepository after every write
  ui/
    theme/               mono palette from mockup
    onboarding/          OnboardingScreen, BudgetSetupScreen, PermissionsScreen
    home/                HomeScreen, BalanceChart (Canvas), TransactionRow
    report/              ReportScreen, DonutChart (Canvas), CategoryShareRow, BudgetStrip, TipCard
    add/                 AddExpenseScreen (keypad)
    review/              ReviewQueueScreen (low-confidence captures)
    settings/            SettingsScreen (permissions, backup, export, delete account)
    Nav.kt
```

### 4.3 Room schema (snake_case columns, matches wire)

```
accounts        id TEXT PK, name, kind (bank|upi|card|cash), last4, currency,
                created_at, updated_at, version, deleted
categories      id TEXT PK, name, emoji, sort_order, discretionary INTEGER (0|1),
                created_at, updated_at, version, deleted
budgets         period TEXT PK (week|month|year), amount_minor INTEGER, currency TEXT,
                created_at, updated_at, version, deleted
transactions    id TEXT PK, account_id FK, category_id FK nullable, amount_minor INTEGER,
                currency TEXT, direction (debit|credit), merchant TEXT, note TEXT,
                occurred_at INTEGER (epoch ms), source (manual|notification|sms|gallery),
                confidence REAL, state (confirmed|review), dedupe_key TEXT UNIQUE,
                created_at, updated_at, version, deleted
sync_cursor     key TEXT PK, value TEXT
```

- `id` is a UUID v4 string generated on device. Same id on every device after sync.
- `amount_minor` stores paise/cents as integer. No floats for money.
- `version` increments on every local edit; `updated_at` is epoch ms UTC. Both drive last-writer-wins sync.
- `deleted` is a soft-delete flag so deletions sync.
- Indexes: `transactions(occurred_at DESC)`, `transactions(state)`, `transactions(dedupe_key)` unique, `transactions(category_id, occurred_at)` for reports.

### 4.4 Capture pipeline

1. A source produces a `RawCapture(source, package_or_sender, text, received_at)`.
2. `RuleEngine.match(raw)` walks the active `RuleSet` (JSON, see section 6), returns `ParsedTransaction?` with `confidence` 0..1.
3. `TransactionRepository.ingest(parsed)`:
   - computes `dedupe_key = sha256(direction|amount_minor|last4|ref_or_merchant|occurred_at rounded to 2 min)`;
   - inserts with `state = confirmed` if confidence >= 0.8 else `review`;
   - unique index on `dedupe_key` silently drops duplicates (SMS and notification for the same UPI payment).
4. Home list shows confirmed rows; a badge links to the review queue.

Income is captured by the same pipeline, not a separate one. Every rule
carries `direction: debit | credit`; credit rules cover UPI received, salary
credits, NEFT/IMPS/RTGS inward, card refunds and cashback, from all four
sources. Home and Report filter by direction via the `expenses | income` toggle.

Sources:
- **Notifications:** `NotificationListenerService` filtered to an allowlist of packages (Google Pay, PhonePe, Paytm, BHIM, Amazon Pay, CRED, major bank apps). The allowlist lives in the ruleset so it updates without a release.
- **SMS (`foss` flavor only):** `RECEIVE_SMS` broadcast receiver plus a one-time `READ_SMS` backfill of the last 90 days on opt-in. Sender filtered by the ruleset's `sms_sender_patterns` (e.g. `*-HDFCBK`, `*-SBIUPI`). The `play` flavor has no SMS permission in its manifest at all, because Play rejects even a declared-but-unused permission. Because UPI apps post a notification for every payment and banks SMS the same payment, the `play` flavor still captures most UPI and card spend; it misses NEFT/IMPS/salary credits that only arrive by SMS, which users add via the keypad or the gallery scan of a bank screenshot.
- **Gallery:** user grants `READ_MEDIA_IMAGES`; app runs ML Kit text recognition on images the user picks (phase 1: manual pick via Photo Picker, no background scan). Extracted text goes through the same `RuleEngine` with a `gallery` source and a receipt-oriented rule family.
- **Manual:** keypad screen, always `confirmed`, confidence 1.

### 4.5 UI (from mockup)

- Monochrome: white background, black text, pill-shaped chips, one accent colour only for the category emoji chip.
- **Onboarding:** step 1 illustration placeholder, floating category chips, "Spent - your minimal budgeting app", Continue with Google, or "Skip, use offline". Step 2 budget setup: three amount fields (week, month, year), currency from locale, "Skip for now" allowed. Step 3 permission consent per capture source with purpose text.
- **Home:** period selector chip (`this month`), search icon, big balance, line chart of daily running balance (Compose Canvas, no chart library), month axis, grouped list by day with emoji, merchant, sub-label, amount. Bottom bar: list, wallet, `+`, chart, settings.
- **Add:** expenses/income toggle, 5-day date strip, amount, category + merchant chips, 3x4 keypad, tick saves.
- **Report:** month selector, `expenses | income` toggle, a donut chart (Canvas) of category split with the total in the centre, tap or drag a segment to show its percentage. Below, one row per category: emoji, name, share of total, amount, change versus last month as a small `+12%` chip, and a thin bar for share. Segments use greys from `#111` to `#CCC` with the selected one in black, so it stays monochrome. Data comes from one SQL `GROUP BY category_id` over the month, plus the same query for the previous month for the delta.
  - **Budget strip** above the donut: three pills `week / month / year`, each showing spent over limit as a thin progress line, tap to edit. Budgets are set in onboarding and editable here.
  - **Tip card** in the top-right corner of the donut, rule-based, no network:
    1. If spend in the selected period <= budget: "On track, X left this month."
    2. Else find the category with the largest increase versus last month among categories flagged `discretionary` (food delivery, shopping, entertainment, subscriptions, travel). Suggest the cut needed: `cut = min(overspend, category_amount)`, phrased "Food delivery is up 40%. Trim it by 1,200 to stay in budget."
    3. If no discretionary category grew, fall back to the largest discretionary category.
    Pure function `budgetTip(period, spent, limit, categoryTotals, prevTotals): Tip`, unit-tested with fixtures. `ponytail:` rule-based; swap in an on-device model if users ask for smarter advice.
- **Review queue:** low-confidence captures with "Looks right" / "Edit" / "Not a transaction".
- **Settings:** permission toggles for each source, backup on/off with passphrase, export/import database file (also used when switching between play and foss builds), sign-out, delete account.
- **Widgets (Jetpack Glance):** `QuickAdd` 1x1 opens the keypad via deep link `ledger://add`; `Balance` 2x2 shows month balance and the month budget progress line, same monochrome style. Both read one `WidgetSnapshot(balance_minor, month_spent_minor, month_budget_minor)` from a DAO query and are refreshed by `WidgetRefresh.requestUpdate()` after every repository write. iOS gets WidgetKit equivalents over the same data in the Swift phase.

### 4.6 Performance (the "blazing fast" bar)

What the fast trackers (Monefy, Money Manager, PennyWise) have in common is
not a clever database but: local-first, a tiny indexed store, zero network on
the hot path, and an add flow that is two taps from launch. Spent copies that
and adds the current Android toolchain on top.

Targets, measured in CI with Macrobenchmark on a Pixel 6a class emulator:

| Metric | Target |
|---|---|
| Cold start to first frame of Home | < 500 ms (p50), < 800 ms (p95) |
| Launch to keypad ready (tap `+`) | 2 taps, < 150 ms screen transition |
| Notification/SMS capture to row in list | < 200 ms |
| Home list scroll | 0 janky frames over 100 rows |
| APK size (play flavor, release) | < 8 MB (ML Kit model downloaded on demand) |

How:
- **Startup path is empty.** `Application.onCreate` only builds `AppContainer` lazily. No analytics, no crash SDK, no Google Sign-In warmup at launch. Rule set loads from assets on a background thread after the first frame.
- **Baseline Profile + Startup Profile** generated by a `benchmark` module, checked into `app/src/main/baseline-prof.txt`, regenerated in CI monthly. Google documents ~30 % faster code execution from first launch.
- **R8 full mode, resource shrinking, no multidex** on the play flavor.
- **Room:** single `SELECT` with a covering index for the Home list (`occurred_at DESC, direction, state`), results as `Flow<List<TransactionRow>>` projected to a small row class, not the full entity. Running balance computed in SQL with a window function, not in Kotlin.
- **Compose:** stable `key = id` on `LazyColumn`, `@Immutable` row models, `derivedStateOf` for the balance header, charts drawn with `Canvas` on a <= 60-point downsample, no chart library.
- **Capture:** parsing runs on a single-thread dispatcher so the regex engine stays hot; rules are pre-compiled `Regex` objects, compiled once at rule-set load.
- **No Hilt, no Dagger:** manual wiring avoids annotation processing on every build and a few ms of graph construction at launch.
- **Strict mode in debug** fails the build on main-thread disk or network.

## 5. Backend service

### 5.1 Layout (mirrors foodxp-b2c-service)

```
service/
  src/index.ts                 Hono app, middleware, routes
  src/middleware/{proxy,auth,request-logger,rate-limit}.ts
  src/helpers/{mongodb,mongoclient,response,logger}.ts
  src/routes/auth/google.ts
  src/routes/sync/{push,pull}.ts
  src/routes/rules/index.ts
  src/routes/me/index.ts
  src/types/*.ts
  tests/ (vitest + mongodb-memory-server)
  api/index.ts                 Vercel entry
  .env.example
```

Conventions copied verbatim from foodxp `Coding Rules.md`: snake_case JSON and Mongo fields, kebab-case files, `#helpers/*` imports, Zod `.strict()` on every body, `{ success, data }` / `{ success: false, error }` envelope, `MongoClient*` helpers only, `Date` objects in Mongo, no `as any`.

### 5.2 Collections (database `spent`)

```
core_users     _id, google_sub (unique), email, name, created_at, deleted_at?
devices        _id, user_id, platform (android|ios), app_version, last_seen_at
sync_rows      _id: `${user_id}:${row_id}`, user_id, table (accounts|categories|budgets|transactions),
               row_id, version, updated_at, ciphertext (base64), nonce (base64), deleted
               index: (user_id, updated_at), unique (user_id, row_id)
parser_rules   version (int, unique), rules (JSON), published_at
request_logs   TTL 7 days (same as other services)
```

`ciphertext` is AES-256-GCM of the row JSON, key derived on device from the user's backup passphrase via PBKDF2-HMAC-SHA256 (600k iterations, salt = user id). Server stores and relays only.

### 5.3 Endpoints

| Method | Path | Auth | Body / Query | Response |
|---|---|---|---|---|
| POST | `/api/auth/google` | none | `{ id_token }` | `{ access_token, user: { id, email, name } }` (JWT HS256, 30 d) |
| POST | `/api/sync/push` | JWT | `{ device_id, rows: [{ table, row_id, version, updated_at, ciphertext, nonce, deleted }] }` max 500 rows | `{ accepted: n, rejected: [{ row_id, server_version }] }` |
| GET | `/api/sync/pull?since=<epoch_ms>&limit=500` | JWT | | `{ rows: [...], next_since }` |
| GET | `/api/rules?since_version=<n>` | none | | `{ version, rules }` or 304 |
| DELETE | `/api/me` | JWT | | 204; deletes user, devices, sync_rows |
| GET | `/api/health` | none | | `{ status: 'ok' }` |

Conflict rule: push accepts a row if `incoming.version > stored.version`, else rejects with the stored version so the client can merge (client re-reads and bumps). Simple LWW; good enough for a single-user ledger.

## 6. Shared parser ruleset

`shared/parser-rules/rules.json` is the single source of truth, consumed by the Kotlin `RuleEngine` (bundled copy in assets, updated from `/api/rules`), by backend tests, and later by the Swift port.

```json
{
  "version": 1,
  "notification_packages": ["com.google.android.apps.nbu.paisa.user", "com.phonepe.app", "net.one97.paytm"],
  "sms_sender_patterns": ["^[A-Z]{2}-HDFCBK$", "^[A-Z]{2}-SBIUPI$", "^[A-Z]{2}-ICICIB$"],
  "rules": [
    {
      "id": "hdfc-upi-debit",
      "source": "sms",
      "match": "(?i)Rs\\.?\\s?(?<amount>[\\d,]+\\.?\\d*) debited from a/c \\*\\*(?<last4>\\d{4}) .* to (?<merchant>[^.]+) ref (?<ref>\\d+)",
      "direction": "debit",
      "confidence": 0.95
    }
  ]
}
```

Named groups are fixed: `amount`, `last4`, `merchant`, `ref`, `date`. Fixtures in `shared/parser-rules/fixtures/*.json` pair sample texts (synthetic, no real data) with expected output; both Kotlin and Vitest tests run the same fixtures so a rule can never drift between platforms.

## 7. Auth and security

- Google Sign-In via Credential Manager -> ID token -> `/api/auth/google` verifies with Google's JWKS using `jose` -> app JWT stored in `EncryptedSharedPreferences`.
- Offline mode without sign-in is fully supported; sign-in only unlocks backup/sync.
- Backup passphrase never leaves device; losing it means losing cloud backup (stated in UI). `ponytail:` no recovery flow in phase 1, add key escrow with a second factor if users ask.
- Rate limit on `/api/auth/*` and `/api/sync/*` keyed by user id or IP, same helper pattern as foodxp.

## 8. Compliance stance (phase 1, no legal spend)

- India DPDP Act 2023: explicit consent screen per data source, purpose stated, revocable in Settings, data deletion endpoint, no processing of raw text server-side.
- GDPR basics for international users: same consent + delete + export (export = share CSV from device).
- Google Play: notification access prominent-disclosure dialog, Data Safety form answers derived from section 5.2, account deletion URL requirement met by `DELETE /api/me` plus in-app button. No SMS permission in the Play build.
- RBI/AA: not applicable until bank linking phase.

## 9. Testing

- Backend: Vitest with mongodb-memory-server for every route; fixture-driven parser tests.
- Android: JVM unit tests for `RuleEngine` (fixtures), `dedupe_key`, `RowCrypto` round-trip, sync merge, `budgetTip`; Room DAO tests on Robolectric; one Compose UI test per screen for render + primary action; Macrobenchmark for cold start.
- CI: GitHub Actions, free tier: `service` typecheck+test, `android` unit tests + assembleDebug for both flavors.

## 10. Prior art consulted

- PennyWise AI (open source, Kotlin, Compose, Room, regex per bank, optional on-device LLM). Studied for architecture only; clean-room implementation, no code reuse (licence is ambiguous between AGPL and MIT across listings).
- UPI Expense Tracker (open source) uses an Accessibility Service and is barred from Play for that reason. Confirms notification access is the narrower, Play-viable route.
- BudgetBakers Wallet reads Google Pay notifications via notification access on Play, which confirms the notification route is accepted with a disclosed core feature.
- Monefy and Money Manager: speed comes from local-only data and a two-tap add flow, plus file-based backup to the user's own Drive. Spent keeps the two-tap flow and local-only data.

Sources: [Finny SMS tracker comparison](https://getfinny.app/blog/sms-expense-tracking-app), [PennyWise write-up](https://dev.to/sarim2000/how-i-built-a-privacy-first-expense-tracker-with-100-on-device-ai-1bf), [PennyWise repo](https://github.com/sarim2000/pennywiseai-tracker), [UPI Expense Tracker on F-Droid](https://f-droid.org/packages/com.goushik.upiwallet/), [BudgetBakers notification access](https://support.budgetbakers.com/hc/en-us/articles/27357560590098-Notification-Access-Permission), [Play SMS/Call Log policy](https://support.google.com/googleplay/android-developer/answer/9047303), [Play legacy SMS exception](https://support.google.com/googleplay/android-developer/answer/9888170), [Android app startup best practices](https://developer.android.com/topic/performance/appstartup/best-practices), [Compose performance](https://developer.android.com/jetpack/compose/performance).

## 11. Out of scope (phase 2+)

Per-category budgets, recurring bills, live FX, Account Aggregator / Plaid linking, background gallery scanning, iOS app and iOS widgets (Swift + WidgetKit), Wear OS.
