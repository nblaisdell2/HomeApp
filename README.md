# Home app

A personal Android app for a wall-mounted tablet and a phone (to-dos, calendar, weather, shopping
list, Evercent), backed by a small API. Design: Confluence "Home app" page; work tracked in the
Jira **HOME** project.

```
api/        Fastify (TypeScript) API + PostgreSQL. Deployed to Railway.
android/    Kotlin + Jetpack Compose app (Hilt, Room, WorkManager, Retrofit).
openapi/    openapi.json exported from the API; the app's Kotlin client is generated from it.
scripts/    install.ps1 (build + install on devices), set-device-key.ps1
```

How data flows: each device keeps a Room cache and edits it offline. A sync job uploads pending
edits and pulls server changes (newest edit wins). While the app is open it holds a Server-Sent
Events connection, so a change on one device shows on the other within a few seconds.

## One-time setup

| What | How |
|---|---|
| Android Studio | Already installed. It bundles the JDK (JBR) used for command-line builds. |
| Docker Desktop | Runs the local Postgres. Start it before working on the API. |
| Node 22+ | `node -v` |
| `adb` on PATH (optional) | Add `%LOCALAPPDATA%\Android\Sdk\platform-tools` to PATH. The scripts find adb without it. |
| Emulators | Device Manager in Android Studio: a phone and a tablet (a `Home_Tablet_API_36` AVD exists). |
| Real devices | Settings → About → tap *Build number* 7 times → Developer options → turn on **USB debugging** (and **Wireless debugging** to install without a cable). Accept the "Allow USB debugging?" prompt the first time. |

## Run the API locally

```powershell
docker compose up -d          # Postgres 17 on localhost:5433
cd api
npm install
copy .env.example .env        # first time only
npm run migrate
npm run dev                   # http://localhost:3000, API docs at http://localhost:3000/docs
```

Other API commands:

```powershell
npm test                                   # route tests against a throwaway home_test database
npm run issue-key -- --name tablet         # prints a device API key once (add --rotate to replace)
npm run revoke-key -- --name tablet        # revoke; with no --name, lists devices and last-seen times
npm run openapi:export                     # after changing routes/schemas; then rebuild the app
```

## Build and install the app

```powershell
.\scripts\install.ps1                      # debug build on every connected device/emulator
.\scripts\set-device-key.ps1 -Key home_...  # first time on each device (or paste the key in Settings)
```

Debug builds ("Home (debug)") call `http://localhost:3000` on the device. `install.ps1` runs
`adb reverse tcp:3000 tcp:3000` so that reaches the API on your PC, over USB, wireless debugging or
the emulator. If the device reconnects or reboots, run `install.ps1` again (or just that
`adb reverse` command).

Android Studio works too: open the `android/` folder, pick a device, press Run. Then run
`adb reverse tcp:3000 tcp:3000` once.

Command-line Gradle needs the bundled JDK:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
cd android
.\gradlew :app:assembleDebug :app:testDebugUnitTest
```

### Wireless debugging (tablet on the wall)

On the device: Developer options → Wireless debugging → *Pair device with pairing code*. Then on
the PC:

```powershell
adb pair 192.168.1.50:37123      # the IP:port and code shown in the pairing dialog
adb connect 192.168.1.50:41234   # the IP:port shown on the Wireless debugging screen
```

## Release builds (the version that stays installed)

Release builds ("Home") talk to the Railway API and must be signed with your own key.

### Release signing (one time)

Create the keystore **outside the repo** with a password you choose, and back up both the `.jks`
file and the password (e.g. in your password manager). If either is lost, updating the app means
uninstalling it first, which wipes its local data.

```powershell
mkdir $HOME\.android-keys
& 'C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe' -genkeypair -v -keystore $HOME\.android-keys\home-release.jks -alias home -keyalg RSA -keysize 4096 -validity 36500
```

Then create `android/keystore.properties` (gitignored):

```properties
storeFile=C:/Users/<you>/.android-keys/home-release.jks
storePassword=<your password>
keyAlias=home
keyPassword=<your password>
```

…and set the Railway URL in `android/local.properties`:

```properties
home.releaseApiBaseUrl=https://<your-api>.up.railway.app/
```

Install with `.\scripts\install.ps1 -Release`, then set the key with
`.\scripts\set-device-key.ps1 -Release -Key home_...` (keys come from the production database, see
below).

Installing over adb from the PC isn't affected by Android's upcoming developer-verification rule.
That rule only applies to installing APK files on the device itself.

## Deployment (Railway)

The API runs on Railway (project `home-app`) as an always-on service, `api`, with a managed
Postgres in the same project over private networking. Railway builds `api/` on every push to `main`
that touches `api/**`, runs `npm run migrate:prod` before each deploy, and health-checks `/health`.
These settings live on the service in the Railway dashboard (Settings tab), not in a file.

Issue a production device key from your PC:

```powershell
cd api
railway link                                   # once: pick the Home project and the API service
railway run --service Postgres -- cmd /c "set DATABASE_URL=%DATABASE_PUBLIC_URL%&& npm run issue-key -- --name tablet"
```

## Changing the API

1. Edit the route or schema in `api/src`, then run `npm test`.
2. Run `npm run openapi:export` to update `openapi/openapi.json`.
3. Rebuild the app. The Kotlin client is regenerated, and anything that no longer matches the API
   fails to compile.
4. Commit and push. Railway deploys the API.
