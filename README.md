# Spent

Minimal, offline-first expense tracker for Android. Captures expenses and income
automatically from payment-app notifications, bank SMS (foss build) and gallery
screenshots. All data stays on the phone; cloud backup is end-to-end encrypted.

| Folder | What |
|---|---|
| `android/` | Kotlin + Jetpack Compose app, Room database, flavors `play` and `foss` |
| `service/` | Hono + MongoDB backend on Vercel: Google auth, encrypted sync, parser rules |
| `shared/parser-rules/` | JSON ruleset and fixtures used by every platform |
| `docs/` | Design spec and implementation plan |

## Run

```bash
cd service && npm install && cp .env.example .env && npm run dev
```

```bash
cd android && ./gradlew assemblePlayDebug
```

Design: `docs/superpowers/specs/2026-10-12-spent-design.md`
Plan: `docs/superpowers/plans/2026-10-12-spent-phase1.md`
