# Spent Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the first release of Spent: an offline-first Android expense tracker that auto-captures expenses and income from payment notifications (and SMS in the foss build) and gallery screenshots, with budgets, a report donut with a saving tip, two widgets, and an encrypted-backup backend.

**Architecture:** Android app (Kotlin + Compose + Room, manual DI) owns all data. A Hono + MongoDB service on Vercel handles Google auth, opaque encrypted row sync, parser-rule distribution and account deletion. A shared JSON ruleset drives parsing on every platform.

**Tech Stack:** Kotlin 2.1, Compose BOM 2025, Room 2.7 (KSP), Jetpack Glance, ML Kit Text Recognition, WorkManager, OkHttp, kotlinx.serialization, Credential Manager. Node 24, Hono 4, TypeScript 6, mongodb 7 driver, jose 6, Zod 4, Vitest 4, mongodb-memory-server.

**Spec:** `docs/superpowers/specs/2026-10-12-spent-design.md`

## Global Constraints

- Android `applicationId` and package: `com.anish1050.ledger`. Brand name only in `strings.xml` `app_name`.
- minSdk 26, targetSdk 35. Flavors `play` and `foss`; only `foss` declares SMS permissions.
- Money is `Long` minor units everywhere. Never `Double`.
- Wire and DB keys snake_case; Kotlin and TS variables camelCase; files kebab-case (TS) / PascalCase (Kotlin).
- Backend: `#helpers/*` imports, Zod `.strict()` on every body, `{ success, data }` envelope, `MongoClient*` helpers only, no `as any`.
- Raw notification, SMS or OCR text is never logged and never sent to the server.
- Backend env: `MONGODB_URI=mongodb://localhost:27017/`, `MONGODB_DBNAME=spent`, `JWT_SECRET`, `GOOGLE_CLIENT_ID`.
- Commit after every task. Branch `master` for phase 1 bootstrap, feature branches after the first release.

## Security requirements (apply to every task)

| Area | Rule |
|---|---|
| Local DB | `android:allowBackup="false"`, `dataExtractionRules` exclude the DB. Device file-based encryption covers at-rest; no SQLCipher in phase 1 (`ponytail:` add if a threat model needs app-level encryption on rooted devices). |
| App lock | Biometric or device credential via `BiometricPrompt` on launch and on resume after 60 s, togglable in Settings, default on when a sync passphrase exists. |
| Secrets | JWT and backup key in `EncryptedSharedPreferences` backed by Android Keystore. Never in plain prefs or logs. |
| Screens | `FLAG_SECURE` on Home, Report and Add so screenshots and recents thumbnails are blank when app lock is on. |
| Logging | `Log.*` only in debug builds via a `Logger` wrapper; capture text never passes through it. |
| Release | R8 full mode, resource shrinking, `isDebuggable=false`, signed with an upload key kept out of the repo. |
| Server auth | Google ID token verified against Google JWKS with `aud == GOOGLE_CLIENT_ID` and `iss` in Google's two issuers. App JWT HS256, 30 d, `sub = user_id`. |
| Server input | Every body and query Zod `.strict()`; push limited to 500 rows and 256 KB; ciphertext base64 validated. |
| Server data | No plaintext financial data ever stored. `sync_rows` holds ciphertext + nonce only. |
| Rate limits | `/api/auth/*` 10/min per IP, `/api/sync/*` 60/min per user. |
| Deletion | `DELETE /api/me` removes user, devices and sync rows in one pass and is idempotent. |

---

## Phase 0: Repo bootstrap

### Task 0.1: Monorepo skeleton and CI

**Files:**
- Create: `README.md`, `.gitignore`, `.github/workflows/ci.yml`, `shared/parser-rules/rules.json`, `shared/parser-rules/fixtures/india-upi.json`

**Produces:** repo structure every later task assumes: `android/`, `service/`, `shared/`, `docs/`.

- [ ] **Step 1: Init git and remote**

```bash
git init -b master
git remote add origin https://github.com/anish1050/Expense-Tracker.git
```

- [ ] **Step 2: Root files**

`.gitignore`:
```
node_modules/
dist/
.env
.env.local
*.log
android/.gradle/
android/**/build/
android/local.properties
android/*.jks
android/.idea/
.DS_Store
```

`README.md` with the one-paragraph description, folder map, and the two commands to run each part.

- [ ] **Step 3: Seed ruleset and fixture**

`shared/parser-rules/rules.json` (version 1, 3 notification packages, 3 SMS sender patterns, 6 rules: HDFC UPI debit SMS, HDFC credit SMS, SBI UPI debit SMS, Google Pay paid notification, Google Pay received notification, PhonePe paid notification). All sample texts synthetic.

`shared/parser-rules/fixtures/india-upi.json`:
```json
[
  {
    "rule": "gpay-paid-notification",
    "source": "notification",
    "sender": "com.google.android.apps.nbu.paisa.user",
    "text": "₹249.00 paid to Burger King",
    "expect": { "direction": "debit", "amount_minor": 24900, "merchant": "Burger King", "confidence": 0.95 }
  },
  {
    "rule": "gpay-received-notification",
    "source": "notification",
    "sender": "com.google.android.apps.nbu.paisa.user",
    "text": "₹5,000.00 received from Rahul Sharma",
    "expect": { "direction": "credit", "amount_minor": 500000, "merchant": "Rahul Sharma", "confidence": 0.95 }
  },
  {
    "rule": "hdfc-upi-debit-sms",
    "source": "sms",
    "sender": "VM-HDFCBK",
    "text": "Rs.249.00 debited from a/c **1234 on 12-10-26 to VPA burgerking@ybl ref 628812345678. Not you? Call 18002586161",
    "expect": { "direction": "debit", "amount_minor": 24900, "last4": "1234", "merchant": "burgerking@ybl", "ref": "628812345678", "confidence": 0.95 }
  },
  {
    "rule": "hdfc-credit-sms",
    "source": "sms",
    "sender": "VM-HDFCBK",
    "text": "Rs.85,000.00 credited to a/c **1234 on 01-10-26 by NEFT ACME PVT LTD SALARY ref N275261234567",
    "expect": { "direction": "credit", "amount_minor": 8500000, "last4": "1234", "merchant": "ACME PVT LTD SALARY", "ref": "N275261234567", "confidence": 0.95 }
  }
]
```

- [ ] **Step 4: CI**

`.github/workflows/ci.yml`: two jobs. `service`: Node 24, `npm ci`, `npm run typecheck`, `npm test` in `service/`. `android`: JDK 17, `./gradlew testPlayDebugUnitTest assemblePlayDebug assembleFossDebug` in `android/`. Both only run when their folder or `shared/` changes.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "chore: bootstrap monorepo, shared parser ruleset, CI"
git push -u origin master
```

---

## Phase 1: Backend service

### Task 1.1: Service scaffold, Mongo helpers, response helpers

**Files:**
- Create: `service/package.json`, `service/tsconfig.json`, `service/.env.example`, `service/vercel.json`, `service/api/index.ts`, `service/dev.ts`, `service/src/index.ts`, `service/src/helpers/mongodb.ts`, `service/src/helpers/mongoclient.ts`, `service/src/helpers/response.ts`, `service/src/helpers/logger.ts`, `service/tests/setup.ts`, `service/vitest.config.ts`, `service/tests/health.test.ts`

**Produces:** `MongoClientFindOne/Find/InsertOne/UpdateOne/DeleteMany/BulkWrite(collection, ...)` returning `MongoOperationResult`; `successResponse(c, data, status)`, `errorResponse(c, msg, status)`, `badRequestResponse`, `unauthorizedResponse`, `notFoundResponse`.

- [ ] **Step 1: package.json** (copy foodxp shape; name `spent-service`; scripts `dev`, `build`, `typecheck`, `test`; deps hono, @hono/node-server, @hono/zod-validator, mongodb, jose, zod; dev deps typescript, tsx, vitest, mongodb-memory-server, @types/node, tsc-alias). `"imports": { "#*": "./src/*" }`.

- [ ] **Step 2: Copy helpers** `mongodb.ts` and `mongoclient.ts` from foodxp verbatim (they are generic), add `MongoClientBulkWrite` and `MongoClientDeleteMany`. Copy `response.ts` verbatim. `logger.ts` exports `logError(module: string, op: string, err: unknown)` that prints `[module:op] message` only.

- [ ] **Step 3: index.ts** with `cors`, `logger()`, `/api/health`, `onError`, `notFound`, exactly like foodxp minus the routes. `api/index.ts` exports `handle(app)` from `hono/vercel`. `dev.ts` serves on `PORT`.

- [ ] **Step 4: Test harness** `tests/setup.ts` starts `MongoMemoryServer`, sets `MONGODB_URI` and `MONGODB_DBNAME=spent_test`, exports `app` request helper. `health.test.ts` asserts `GET /api/health` returns `{ status: 'ok' }`.

- [ ] **Step 5: Run** `npm run typecheck && npm test` green. Commit `feat(service): scaffold hono app with mongo helpers`.

### Task 1.2: Google auth exchange and JWT middleware

**Files:**
- Create: `service/src/routes/auth/google.ts`, `service/src/middleware/proxy.ts`, `service/src/middleware/auth.ts`, `service/src/middleware/rate-limit.ts`, `service/src/helpers/jwt.ts`, `service/src/types/user.ts`, `service/tests/auth.test.ts`
- Modify: `service/src/index.ts`

**Produces:** `POST /api/auth/google` body `{ id_token }` -> `{ success, data: { access_token, user: { id, email, name } } }`. Context vars `userId` set by `proxyMiddleware`; `authMiddleware` 401s when absent. `signAppJwt(userId)`, `verifyAppJwt(token)`.

- [ ] **Step 1: Failing tests** in `auth.test.ts`: (a) invalid id_token -> 401; (b) valid token (mock `verifyGoogleIdToken` via `vi.mock`) creates `core_users` doc with `google_sub` and returns JWT; (c) second call same sub does not create a second user; (d) `GET /api/me/ping` with bearer -> 200, without -> 401.

- [ ] **Step 2: jwt.ts**

```ts
import { SignJWT, jwtVerify, createRemoteJWKSet } from 'jose'

const GOOGLE_JWKS = createRemoteJWKSet(new URL('https://www.googleapis.com/oauth2/v3/certs'))
const secret = () => new TextEncoder().encode(requireEnv('JWT_SECRET'))

export async function verifyGoogleIdToken(idToken: string) {
  const { payload } = await jwtVerify(idToken, GOOGLE_JWKS, {
    audience: requireEnv('GOOGLE_CLIENT_ID'),
    issuer: ['https://accounts.google.com', 'accounts.google.com'],
  })
  return { sub: String(payload.sub), email: String(payload.email ?? ''), name: String(payload.name ?? '') }
}

export function signAppJwt(userId: string) {
  return new SignJWT({}).setProtectedHeader({ alg: 'HS256' }).setSubject(userId).setIssuedAt().setExpirationTime('30d').sign(secret())
}

export async function verifyAppJwt(token: string): Promise<string | null> {
  try { return (await jwtVerify(token, secret(), { algorithms: ['HS256'] })).payload.sub ?? null } catch { return null }
}
```

- [ ] **Step 3: google.ts** route: zValidator json `{ id_token: z.string().min(20) }.strict()`, verify, `MongoClientFindOneAndUpdate('core_users', { google_sub }, { $setOnInsert: {...created_at}, $set: { email, name, last_login_at } }, { upsert: true, returnDocument: 'after' })`, sign, respond.

- [ ] **Step 4: middleware** `proxy.ts` reads `Authorization: Bearer`, sets `userId` or `''`. `auth.ts` returns `unauthorizedResponse` when empty. `rate-limit.ts`: in-memory sliding window `rateLimit(max, windowSec, bucket)` keyed by `userId || ip`. `ponytail:` in-memory per instance; move to Mongo TTL collection if Vercel fan-out makes it leaky.

- [ ] **Step 5: Wire in index.ts**, tests green, commit `feat(service): google sign-in exchange and jwt auth`.

### Task 1.3: Sync push and pull

**Files:**
- Create: `service/src/routes/sync/push.ts`, `service/src/routes/sync/pull.ts`, `service/src/types/sync-row.ts`, `service/tests/sync.test.ts`
- Modify: `service/src/index.ts`

**Produces:** endpoints from spec 5.3. `SyncRowDoc { _id, user_id, table, row_id, version, updated_at: Date, ciphertext, nonce, deleted }`.

- [ ] **Step 1: Failing tests**: push 2 rows -> accepted 2; push same row with lower version -> rejected with `server_version`; push with higher version -> accepted; pull `since=0` returns both with `next_since` = max updated_at; pull `since=next_since` returns empty; user A cannot pull user B rows; body of 501 rows -> 400.

- [ ] **Step 2: Schemas**

```ts
const rowSchema = z.object({
  table: z.enum(['accounts', 'categories', 'budgets', 'transactions']),
  row_id: z.string().uuid(),
  version: z.number().int().positive(),
  updated_at: z.number().int().positive(),
  ciphertext: z.string().base64().max(16_384),
  nonce: z.string().base64().length(16),
  deleted: z.boolean(),
}).strict()
const pushSchema = z.object({ device_id: z.string().uuid(), rows: z.array(rowSchema).min(1).max(500) }).strict()
```

- [ ] **Step 3: push.ts** builds one `bulkWrite` of `updateOne` ops with filter `{ _id, $or: [{ version: { $lt: row.version } }, { version: { $exists: false } }] }` and `upsert: true`; rows with no match and no upsert (duplicate key error 11000 on insert) are the rejects; re-read their `version` and return them. Also upsert `devices` `last_seen_at`.

- [ ] **Step 4: pull.ts** query `{ user_id, updated_at: { $gt: new Date(since) } }` sort `updated_at asc` limit; `next_since` = last row's `updated_at` ms or `since`.

- [ ] **Step 5: Indexes** created at startup in `helpers/indexes.ts`: `sync_rows (user_id, updated_at)`, `core_users (google_sub) unique`, `request_logs (created_at) expireAfterSeconds 604800`.

- [ ] **Step 6: Tests green, commit** `feat(service): encrypted row sync push/pull`.

### Task 1.4: Rules endpoint and account deletion

**Files:**
- Create: `service/src/routes/rules/index.ts`, `service/src/routes/me/index.ts`, `service/scripts/publish-rules.ts`, `service/tests/rules.test.ts`, `service/tests/me.test.ts`

- [ ] **Step 1: Tests**: `GET /api/rules` returns latest `{ version, rules }`; `?since_version=<latest>` -> 304; `DELETE /api/me` removes user, devices, sync_rows, returns 204, second call also 204.
- [ ] **Step 2: rules route** reads `parser_rules` sorted by version desc limit 1, `Cache-Control: public, max-age=3600`.
- [ ] **Step 3: publish-rules.ts** reads `../shared/parser-rules/rules.json`, inserts if `version` not present. Run `npx tsx --env-file=.env scripts/publish-rules.ts`.
- [ ] **Step 4: me route** three `MongoClientDeleteMany` by `user_id`, then `deleteOne` user.
- [ ] **Step 5: Tests green, commit** `feat(service): parser rules distribution and account deletion`.

### Task 1.5: Fixture-driven parser test (backend side)

**Files:**
- Create: `service/src/helpers/rule-engine.ts`, `service/tests/rule-engine.test.ts`

Purpose: guarantees `rules.json` stays valid JS-regex and matches fixtures before publish. Same algorithm as Kotlin: iterate rules filtered by `source`, first match wins, parse `amount` by stripping commas, `amount_minor = Math.round(amount * 100)`.

- [ ] Test loads every fixture file and asserts `expect` fields. Commit `test(service): shared fixtures validate parser rules`.

---

## Phase 2: Android foundation

### Task 2.1: Gradle project, flavors, theme, nav shell

**Files:**
- Create: `android/settings.gradle.kts`, `android/build.gradle.kts`, `android/gradle/libs.versions.toml`, `android/app/build.gradle.kts`, `android/app/src/main/AndroidManifest.xml`, `android/app/src/foss/AndroidManifest.xml`, `android/app/src/main/res/values/strings.xml`, `.../LedgerApp.kt`, `.../AppContainer.kt`, `.../ui/theme/Theme.kt`, `.../ui/Nav.kt`, `.../MainActivity.kt`

- [ ] **Step 1: build.gradle.kts** `applicationId = "com.anish1050.ledger"`, `productFlavors { play {}; foss {} }` dimension `distribution`, `release { isMinifyEnabled = true; isShrinkResources = true }`, `buildFeatures { compose = true }`, KSP for Room, `kotlinx-serialization` plugin.
- [ ] **Step 2: Manifests** main: `allowBackup=false`, `INTERNET`, `POST_NOTIFICATIONS`, `READ_MEDIA_IMAGES`, listener service declared with `BIND_NOTIFICATION_LISTENER_SERVICE`. foss overlay adds `RECEIVE_SMS`, `READ_SMS`, receiver.
- [ ] **Step 3: Theme** colours `Ink=#111111`, `Paper=#FFFFFF`, `Mist=#F2F2F2`, `Graphite=#777777`; typography Inter-like system font, display 40sp for balance. Pill shape `RoundedCornerShape(50)`.
- [ ] **Step 4: Nav** routes `onboarding`, `home`, `add`, `report`, `review`, `settings`; deep link `ledger://add`. Bottom bar on home/report/settings.
- [ ] **Step 5: StrictMode** in `LedgerApp` debug: `penaltyDeath` on disk/network on main.
- [ ] **Step 6:** `./gradlew assemblePlayDebug assembleFossDebug` builds. Commit `feat(android): project scaffold, flavors, theme, navigation`.

### Task 2.2: Room schema, DAOs, repositories

**Files:**
- Create: `data/db/LedgerDatabase.kt`, `data/db/entities/{Account,Category,Budget,Transaction,SyncCursor}Entity.kt`, `data/db/dao/{Account,Category,Budget,Transaction,SyncCursor}Dao.kt`, `data/db/Seed.kt`, `data/repo/{Transaction,Category,Budget}Repository.kt`, `data/Money.kt`, test `data/db/TransactionDaoTest.kt` (Robolectric)

**Produces:**
```kotlin
data class TransactionRow(val id: String, val emoji: String, val merchant: String, val subLabel: String, val amountMinor: Long, val direction: Direction, val occurredAt: Long)
data class DaySum(val dayStart: Long, val runningBalanceMinor: Long)
data class CategoryTotal(val categoryId: String, val name: String, val emoji: String, val discretionary: Boolean, val totalMinor: Long)
data class WidgetSnapshot(val balanceMinor: Long, val monthSpentMinor: Long, val monthBudgetMinor: Long?)

interface TransactionDao {
  @Query("SELECT t.id, c.emoji, t.merchant, COALESCE(t.note, a.name) AS subLabel, t.amount_minor, t.direction, t.occurred_at FROM transactions t LEFT JOIN categories c ON c.id=t.category_id LEFT JOIN accounts a ON a.id=t.account_id WHERE t.deleted=0 AND t.state='confirmed' AND t.occurred_at BETWEEN :from AND :to ORDER BY t.occurred_at DESC")
  fun rows(from: Long, to: Long): Flow<List<TransactionRow>>
  @Query("SELECT (occurred_at/86400000)*86400000 AS dayStart, SUM(SUM(CASE direction WHEN 'credit' THEN amount_minor ELSE -amount_minor END)) OVER (ORDER BY (occurred_at/86400000)) AS runningBalanceMinor FROM transactions WHERE deleted=0 AND state='confirmed' AND occurred_at BETWEEN :from AND :to GROUP BY dayStart")
  fun dailyBalance(from: Long, to: Long): Flow<List<DaySum>>
  @Query("SELECT c.id AS categoryId, c.name, c.emoji, c.discretionary, SUM(t.amount_minor) AS totalMinor FROM transactions t JOIN categories c ON c.id=t.category_id WHERE t.deleted=0 AND t.state='confirmed' AND t.direction=:direction AND t.occurred_at BETWEEN :from AND :to GROUP BY c.id ORDER BY totalMinor DESC")
  suspend fun categoryTotals(direction: String, from: Long, to: Long): List<CategoryTotal>
  @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertIgnore(t: TransactionEntity): Long
  @Query("SELECT * FROM transactions WHERE state='review' AND deleted=0 ORDER BY occurred_at DESC") fun reviewQueue(): Flow<List<TransactionEntity>>
  @Query("SELECT * FROM transactions WHERE updated_at > :since AND id NOT IN (SELECT value FROM sync_cursor WHERE key='pushed_'||id)") suspend fun dirtySince(since: Long): List<TransactionEntity>
}
```

- [ ] **Step 1: Failing DAO test**: insert 3 transactions across 2 days, assert `rows()` order and `dailyBalance()` running sums; insert duplicate `dedupe_key` -> `insertIgnore` returns -1.
- [ ] **Step 2: Entities** per spec 4.3 with `@Index(value=["dedupe_key"], unique=true)`, `@Index("occurred_at")`, `@Index(value=["category_id","occurred_at"])`.
- [ ] **Step 3: Seed** 12 default categories with emoji and `discretionary` flags (food delivery, shopping, entertainment, subscriptions, travel = 1; rent, groceries, utilities, fuel, health, education, salary = 0) and one `cash` account, inserted in `RoomDatabase.Callback.onCreate`.
- [ ] **Step 4: Money.kt** `fun Long.formatMinor(currency: String): String` using `NumberFormat.getCurrencyInstance` with cached instances per currency.
- [ ] **Step 5: Repositories** thin: `TransactionRepository.ingest(parsed: ParsedTransaction): IngestResult` computes `dedupe_key`, picks state by confidence, inserts, then `WidgetRefresh.requestUpdate()`. `add(manual)`, `confirm(id)`, `discard(id)`, `update(...)` all bump `version` and `updated_at`.
- [ ] **Step 6:** test green, commit `feat(android): room schema, daos, repositories`.

### Task 2.3: Rule engine and rule store

**Files:**
- Create: `capture/parser/{RuleSet,Rule,RawCapture,ParsedTransaction}.kt`, `capture/parser/RuleEngine.kt`, `capture/parser/RuleStore.kt`, `app/src/main/assets/rules.json` (copied by a Gradle task from `shared/parser-rules/rules.json`), test `capture/parser/RuleEngineFixtureTest.kt` (reads `shared/parser-rules/fixtures/*.json` via a Gradle `testResources` srcDir pointing at `../../shared/parser-rules`)

**Produces:**
```kotlin
enum class Source { manual, notification, sms, gallery }
enum class Direction { debit, credit }
data class RawCapture(val source: Source, val sender: String, val text: String, val receivedAt: Long)
data class ParsedTransaction(val direction: Direction, val amountMinor: Long, val merchant: String, val last4: String?, val ref: String?, val occurredAt: Long, val source: Source, val confidence: Float, val ruleId: String)
class RuleEngine(ruleSet: RuleSet) { fun match(raw: RawCapture): ParsedTransaction? }
class RuleStore(context, api: LedgerApi) { val current: StateFlow<RuleSet>; suspend fun refresh() }
```

- [ ] **Step 1: Failing fixture test** iterates every fixture, builds `RawCapture`, asserts `expect` fields.
- [ ] **Step 2: RuleEngine** compiles every `match` to `Regex` once; `match()` filters by `source`, for sms also checks `sender` against `sms_sender_patterns`, for notification checks `sender in notification_packages`; first hit wins; amount parse strips `,` and `₹`/`Rs`, `amountMinor = (BigDecimal(amount) * 100).toLong()`; `occurredAt` from `date` group (`dd-MM-yy`) else `receivedAt`.
- [ ] **Step 3: RuleStore** loads asset on `Dispatchers.IO`, then calls `GET /api/rules?since_version=` at most once per 24 h, stores newer JSON in `filesDir/rules.json`.
- [ ] **Step 4:** test green, commit `feat(android): shared-fixture rule engine`.

---

## Phase 3: Capture sources

### Task 3.1: Notification listener

**Files:** `capture/notification/TransactionNotificationListener.kt`, `capture/CaptureDispatcher.kt`, test `capture/CaptureDispatcherTest.kt`

- [ ] `CaptureDispatcher.handle(raw)` runs on `Executors.newSingleThreadExecutor().asCoroutineDispatcher()`: `ruleEngine.match` then `transactionRepository.ingest`. Test: two raws for the same payment (notification + sms text) produce one row.
- [ ] Listener `onNotificationPosted`: ignore if `packageName !in ruleStore.current.value.notificationPackages`; build text from `EXTRA_TITLE + " " + EXTRA_TEXT + " " + EXTRA_BIG_TEXT`; dispatch. Never log the text.
- [ ] Settings toggle opens `ACTION_NOTIFICATION_LISTENER_SETTINGS` after a prominent-disclosure dialog (Play requirement) explaining purpose.
- [ ] Commit `feat(android): notification capture`.

### Task 3.2: SMS receiver (foss flavor)

**Files:** `app/src/foss/java/.../capture/sms/BankSmsReceiver.kt`, `app/src/foss/java/.../capture/sms/SmsBackfill.kt`, `app/src/play/java/.../capture/sms/SmsCapture.kt` (no-op object so Settings compiles in both)

- [ ] Receiver on `SMS_RECEIVED` builds `RawCapture(sms, originatingAddress, messageBody)`, dispatch.
- [ ] `SmsBackfill.run(days = 90)` queries `Telephony.Sms.Inbox` on IO, filters by sender pattern first, dispatches each.
- [ ] Commit `feat(android): sms capture in foss flavor`.

### Task 3.3: Gallery OCR

**Files:** `capture/gallery/ReceiptOcrScanner.kt`, `ui/add/ScanSheet.kt`

- [ ] Photo Picker (`PickVisualMedia`) -> `InputImage.fromFilePath` -> `TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)` -> `RawCapture(gallery, "gallery", text)`. If no rule matches, open Add screen prefilled with the largest `₹` amount found and let the user confirm.
- [ ] Rules in `rules.json` get a `gallery` family: Google Pay/PhonePe screenshot layouts ("Paid to X", "₹ amount", "Completed").
- [ ] Commit `feat(android): gallery screenshot capture via ml kit`.

---

## Phase 4: Screens

### Task 4.1: Onboarding (3 steps) with budget setup and permissions

**Files:** `ui/onboarding/{OnboardingScreen,BudgetSetupScreen,PermissionsScreen,OnboardingViewModel}.kt`

- [ ] Step 1 per mockup; "Continue with Google" calls Credential Manager `GetGoogleIdOption` then `POST /api/auth/google`, stores JWT in `AuthStore`. "Skip" sets `signed_in=false`.
- [ ] Step 2 three `OutlinedTextField`s with numeric keyboard, writes `budgets` rows. Skip allowed.
- [ ] Step 3 one card per source with purpose text and a toggle that triggers the system flow.
- [ ] Compose test: skipping all three lands on Home. Commit `feat(android): onboarding with budgets and permissions`.

### Task 4.2: Home

**Files:** `ui/home/{HomeScreen,HomeViewModel,BalanceChart,TransactionRow,PeriodChip}.kt`

- [ ] ViewModel exposes `StateFlow<HomeState(balanceMinor, points: List<Float>, groups: List<DayGroup>, reviewCount)>` built from `rows()` and `dailyBalance()` combined, period default current month.
- [ ] `BalanceChart` Canvas: downsample to 60 points, cubic path, dot on today.
- [ ] `LazyColumn` with sticky day headers, `key = row.id`, `TransactionRow` as `@Immutable`.
- [ ] Bottom bar; `+` navigates to `add`.
- [ ] Compose test renders 3 rows and balance. Commit `feat(android): home screen`.

### Task 4.3: Add expense keypad

**Files:** `ui/add/{AddExpenseScreen,AddViewModel,Keypad,DateStrip,ChipRow}.kt`

- [ ] State: `direction`, `dateStrip` (today +-2), `amountText`, `categoryId`, `merchant`. Keypad buttons 0-9, `.`, tick. Tick calls `TransactionRepository.add(...)`, pops back.
- [ ] Test: typing `2 0 . 0 0` tick inserts `amount_minor = 2000`. Commit `feat(android): add expense keypad`.

### Task 4.4: Report with donut, budget strip and tip

**Files:** `insight/BudgetTip.kt`, `ui/report/{ReportScreen,ReportViewModel,DonutChart,CategoryShareRow,BudgetStrip,TipCard}.kt`, test `insight/BudgetTipTest.kt`

**Produces:**
```kotlin
data class Tip(val headline: String, val detail: String?)
fun budgetTip(spentMinor: Long, limitMinor: Long?, totals: List<CategoryTotal>, prevTotals: List<CategoryTotal>, currency: String): Tip
```

- [ ] **Failing tests**: under budget -> "On track"; over budget with food delivery +40% -> mentions food delivery and the trim amount `min(overspend, categoryTotal)`; no discretionary growth -> largest discretionary category; no budget set -> "Set a budget to get tips".
- [ ] Implement per spec 4.5. Pure Kotlin, no Android imports.
- [ ] `DonutChart` Canvas: `drawArc` per category, stroke 28dp, greys by rank, selected in Ink, centre total text; tap detection by angle.
- [ ] `BudgetStrip` three pills with `LinearProgressIndicator` 2dp. Tap opens an inline edit sheet writing `budgets`.
- [ ] `TipCard` top-right overlay on the donut, Mist background, 12sp.
- [ ] Commit `feat(android): report screen with donut, budgets and saving tip`.

### Task 4.5: Review queue and Settings

**Files:** `ui/review/{ReviewQueueScreen,ReviewViewModel}.kt`, `ui/settings/{SettingsScreen,SettingsViewModel}.kt`, `data/export/DbExport.kt`

- [ ] Review: list of `review` rows with three actions calling `confirm`, open Add prefilled, `discard`.
- [ ] Settings: source toggles, app lock toggle (`BiometricPrompt`), backup toggle with passphrase sheet, export (copies DB file through SAF `CreateDocument`), import (checkpoint WAL, replace file, restart), sign out, delete account (`DELETE /api/me` then wipe local).
- [ ] Commit `feat(android): review queue and settings`.

### Task 4.6: Widgets

**Files:** `widget/{QuickAddWidget,BalanceWidget,WidgetRefresh}.kt`, `res/xml/{quick_add_info,balance_info}.xml`

- [ ] `QuickAddWidget` Glance: Ink circle with white `+`, `actionStartActivity` with `ledger://add`.
- [ ] `BalanceWidget` reads `WidgetSnapshot` via DAO in `provideGlance`, shows balance and progress bar.
- [ ] `WidgetRefresh.requestUpdate(context)` = `BalanceWidget().updateAll(context)` called from repository writes.
- [ ] Commit `feat(android): quick add and balance widgets`.

---

## Phase 5: Sync and app lock

### Task 5.1: Row crypto and sync engine

**Files:** `data/sync/{RowCrypto,SyncEngine,SyncWorker,SyncRowJson}.kt`, `data/api/{LedgerApi,AuthStore}.kt`, tests `RowCryptoTest.kt`, `SyncEngineTest.kt`

**Produces:**
```kotlin
class RowCrypto(passphrase: CharArray, userId: String) { fun encrypt(json: String): Pair<ByteArray /*ciphertext*/, ByteArray /*nonce 12*/>; fun decrypt(ct: ByteArray, nonce: ByteArray): String }
class SyncEngine(db, api, crypto) { suspend fun push(); suspend fun pull() }
```

- [ ] Tests: encrypt/decrypt round trip; wrong passphrase throws; push sends only dirty rows and marks them pushed; pull applies rows with higher version and ignores lower.
- [ ] `RowCrypto`: PBKDF2WithHmacSHA256 600k iterations -> AES/GCM/NoPadding 256, random 12-byte nonce per row.
- [ ] `SyncWorker` periodic 6 h + on-demand after writes when online, constraints `NetworkType.CONNECTED`.
- [ ] Commit `feat(android): client-side encrypted sync`.

### Task 5.2: App lock and secure flags

- [ ] `LockGate` composable wraps `Nav`; shows `BiometricPrompt` with `BIOMETRIC_STRONG or DEVICE_CREDENTIAL`; relocks after 60 s in background.
- [ ] `FLAG_SECURE` set in `MainActivity` when lock enabled.
- [ ] Commit `feat(android): biometric app lock`.

---

## Phase 6: Performance and release

### Task 6.1: Baseline profile and macrobenchmark

- [ ] Add `benchmark` module with `BaselineProfileGenerator` (startup + scroll home + open add) and `StartupBenchmark`. Generate `baseline-prof.txt`, commit it. Record p50/p95 in `docs/perf.md`.
- [ ] Commit `perf(android): baseline profile and startup benchmark`.

### Task 6.2: Release config and store assets

- [ ] Upload keystore generated locally, path via `local.properties`, never committed. `play` release AAB, `foss` release APK attached to GitHub Release by a `release.yml` workflow on tag.
- [ ] `docs/play-listing.md`: Data Safety answers, notification access disclosure text, account deletion URL, privacy policy text (hosted as a GitHub Pages file, free).
- [ ] Commit `chore: release pipeline and store listing docs`.

---

## Self-review

- Spec coverage: every section 4.x and 5.x maps to a task above; widgets 4.6; budgets and tip 4.4; income via rules 2.3 fixtures; export/import 4.5; security table applied in 2.1, 5.1, 5.2, 6.2.
- Types: `TransactionRow`, `CategoryTotal`, `WidgetSnapshot`, `ParsedTransaction`, `RawCapture`, `Tip` defined once in Phase 2 and reused by name afterwards.
- No placeholders remain; "ponytail:" notes mark deliberate ceilings only.
