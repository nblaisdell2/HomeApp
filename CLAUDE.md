# Home app: notes for Claude

Monorepo: `api/` (Fastify 5 + TypeScript + Postgres), `android/` (Kotlin, Compose, Hilt, Room,
WorkManager, Retrofit), `openapi/openapi.json` (generated from the API, input to the Android
client generator). Jira project HOME; README.md covers setup and commands.

## Conventions

- API: TypeBox schemas in `api/src/schemas`; shared ones are registered with `$id` and referenced
  with `ref()` so they become named OpenAPI components. SQL lives in `api/src/repositories`, never
  in route handlers. Plugins and routes are registered explicitly in `api/src/app.ts` (no autoload,
  because autoload breaks under vitest).
- After any route/schema change: `cd api && npm test && npm run openapi:export`, then rebuild Android.
- Android never calls the network from screens. Screens read Room through repositories; writes
  mark rows `pendingSync` and call `SyncScheduler.requestSync()`. `SyncEngine` does push-then-pull
  with last-write-wins on `modifiedAt` and a server `version` cursor.
- Generated client: `com.nblaisdell.home.data.remote.generated.*` under `app/build/generated`.
  Don't edit it; skip files via `android/app/openapi-generator-ignore`.
- Debug builds use `http://localhost:3000/` plus `adb reverse tcp:3000 tcp:3000`. Emulator access
  via 10.0.2.2 was unreliable on this machine: slow connects, and long-lived connections dropped silently.

## Commands

- API tests: `cd api && npm test` (needs `docker compose up -d`)
- Android: set `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`, then in `android/`
  `./gradlew :app:assembleDebug :app:testDebugUnitTest`
- Install on devices: `scripts/install.ps1` (`-Release` for the signed build)
