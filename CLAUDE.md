# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build
./gradlew assembleDebug
./gradlew assembleRelease

# Run tests
./gradlew test                    # unit tests
./gradlew connectedAndroidTest    # instrumented tests (device/emulator required)

# Run a single test class
./gradlew test --tests "com.an0obis.comuginator.ExampleUnitTest"

# Lint
./gradlew lint
```

On Windows use `gradlew.bat` instead of `./gradlew`.

## Architecture

Single-module Android app (`com.an0obis.comuginator`), Kotlin, minSdk 24, targetSdk 36. The UI is traditional Views (XML layouts + RecyclerView), not Compose — Compose is a declared dependency but not used for any current screens.

### Package layout

- **`api/`** — Retrofit `ApiService` + `ApiClient` singleton + all DTOs in `Dto.kt`. `SuggestedReplyItem` is a marker interface shared by `AacCardDto` and `WaitStepDto` so both can appear in a suggested-reply list.
- **`service/`** — WorkManager workers (`CommandSyncWorker`, `TelemetryWorker`, `FcmTokenSyncWorker`), their corresponding scheduler objects, broadcast receivers (`BootReceiver`, `PowerConnectionReceiver`), and the FCM service.
- **`storage/`** — Three thin SharedPreferences wrappers: `SessionStore` (auth token, userId, deviceId, role, language), `SettingsStore` (UI preferences), `FcmTokenStore` (pending FCM token).
- **`ui/`** — Activities + adapters. `BaseActivity` is the central base class. The only ViewModel in use is `ComposeMessageViewModel` (holds mutable compose state across config changes).

### BaseActivity responsibilities

`BaseActivity` handles concerns that every screen needs:
- **Role guard**: if `SessionStore.role == "CHILD"` and the current activity is not exempted, it immediately redirects to `ChildHomeActivity`.
- **Notification permission** + scheduler initialization on first `ensureInitialized()` call.
- **Pending message check** on `onResume` — queries `/v1/commands/pending` and `/v1/messages/aac`, finds unanswered incoming messages or unreceived replies, and opens `IncomingMessageActivity` automatically.
- **401 handling** (`handleUnauthorized`): clears session and returns to `MainActivity`.
- **Protected image loading** (`loadProtectedImage`): injects the Bearer token into Coil image requests.

### Auth flow

`SessionStore.authHeader()` / `authHeaderOrThrow()` produce the `"Bearer <token>"` string. The token is obtained once during family creation or join (`MainActivity`) and persisted indefinitely. All `ApiService` methods take an `@Header("Authorization")` parameter — pass `store.authHeaderOrThrow()`.

`ApiClient` also exposes two raw OkHttp helpers (`loadBitmap`, `getAacMessageWithAuthHeader`) for cases that need synchronous blocking calls from a background thread (e.g. inside `CommandSyncWorker`).

### Background work pipeline

FCM push with `type=sync_commands` → `CommandSyncScheduler.enqueueImmediate` → `CommandSyncWorker` polls `/v1/commands/pending` → dispatches by `command.type`:
- `set_volume` — adjusts `AudioManager.STREAM_MUSIC`
- `aac_message_available` — shows notification via `NotificationHelper`
- `aac_reply_available` — shows notification
- `invite_used` — stores `lastUsedInviteId` and broadcasts `ACTION_INVITE_USED`

All commands are ACKed with `/v1/commands/{id}/ack` after handling. Schedulers use `ExistingWorkPolicy.REPLACE` to prevent duplicate runs.

`TelemetryWorker` sends periodic heartbeats (battery %, volume %, device metadata) to `/v1/devices/heartbeat`. It also runs immediately on app start and on power-connection events.

### Message model

`AacCardDto` is the core data unit — a pictogram card with `id`, `label`, `imageUrl`, `source`, and `sourceRef`. Messages have two modes:
- **NORMAL** — a single question or statement; the recipient picks one suggested reply.
- **SEQUENCE** — an ordered plan; steps are walked through in order and can include `WAIT` timer steps (`source="WAIT"`, `sourceRef=seconds`). A WAIT step is rendered with `TimerDrawable` and auto-advances after the countdown.

When composing, `WaitStepDto` is used in the API request body for timer steps, but `AacCardDto` (with `source="WAIT"`) is used internally in the UI.

### Role system

Two roles: `PARENT` and `CHILD`. `CHILD` devices run `ChildHomeActivity` exclusively, which shows a hierarchical grid of shortcut nodes. Parents configure this grid in editor mode (`EXTRA_EDITOR_MODE=true`); children use it in viewer mode. Tapping a node sends a request action to the parent's device.

### Library

Family photo uploads and ARASAAC pictogram search both produce `AacCardDto` items stored server-side. Items can be organized into named sets (`LibrarySetDetailsDto`). ARASAAC search uses the app language (`SessionStore.appLanguage`) to pick `en`/`es`/`ru` results.

### Responsive layout

Grid column counts are set via `res/values/integers.xml` with overrides at `values-w600dp` and `values-w720dp` to adjust for tablets.

### Backend

REST API base URL: `http://217.154.185.59/` (plain HTTP — `usesCleartextTraffic="true"` is set in the manifest). All endpoints are under `/v1/`.
