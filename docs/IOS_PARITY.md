# iOS ↔ Android Feature Parity Checklist

Goal: bring the iOS app to **full feature parity** with the shipped Android
v2.4.0 "Life OS" app, by **reusing the shared Kotlin Multiplatform module**
(`:shared`) and building SwiftUI screens on top — not reimplementing business
logic in Swift.

Ground truth is the Android app (`androidApp/`). This doc is the working
checklist; it is updated as each area lands.

## Architecture decision

- **Reuse `:shared`.** The Mac build (Xcode 26.6, macOS 26.5, JDK 21 + Android
  SDK 36 provisioned into `~` on the host) produces `Shared.framework` via the
  Kotlin/Native `linkDebugFrameworkIosArm64` / `embedAndSignAppleFrameworkForXcode`
  tasks. iOS is **Intel x86_64** → device builds use `iosArm64`, simulator uses
  `iosX64`. (The earlier CI shortcut dropped the framework and shipped a
  pure-Swift client; we are re-adding it.)
- **Interop rules** (Kotlin/Native ⇄ Swift, no SKIE):
  - `suspend fun` → Swift `async throws` (bridges natively). ✅
  - `Flow<T>` does **not** bridge to Swift ergonomically. We add a Kotlin
    **iOS facade** (`shared/src/iosMain/.../ios/LifeAgentIos.kt`) that turns each
    stream into a `suspend fun … (onEvent: (T) -> Unit)` — the supported
    direction (Swift *calls* Kotlin; Kotlin collects the Flow). ✅
  - Kotlin default args don't cross the bridge → the facade exposes
    zero-/explicit-arg factories (mirrors existing `IosFactories`).
- **Secure storage**: `EncryptedKeyValueStorage` over `IosSecretKeyProvider`
  (Keychain + Secure Enclave + CryptoKit AES-GCM), same seam Android uses.
- **Voice**: iOS substitution noted below.
- **Notifications**: `UNUserNotificationCenter` replaces Android's WorkManager +
  NotificationManager; the shared `HermesReminderPoller` logic is reused.

## Phases (each = a commit; build on the Mac after each chunk)

- [x] **P0 — Toolchain + version fix.** Committed project.yml version/launch keys;
      provisioned JDK 21 + Android SDK 36 on the Mac; `Shared.framework` (iosArm64)
      builds and embeds; app links against it (16 MB KMP binary, `@rpath`).
- [x] **P1 — Framework wiring.** iOS facade `shared/iosMain/.../ios/LifeAgentIos.kt`;
      project.yml embeds `Shared.framework` via `embedAndSignAppleFrameworkForXcode`;
      app archives to an unsigned `.ipa`.
- [x] **P2 — Connect / onboarding** (Hermes URL + key, Keychain via shared
      `HermesConfigStore`, `/health` test, plaintext-remote warning).
- [x] **P3 — Life OS dashboard** (time greeting + name; Goals/Tasks/Memos/
      Reminders cards; stale-while-revalidate home cache; task check-off; FAB).
- [x] **P4 — Chat** (streaming via facade Flow→callback, memory session headers,
      persistence via `ChatStore`, empty-state suggestion cards, copy/share, markdown,
      crisis card).
- [x] **P5 — History** (list, multi-select, delete, hydrate-from-`/api/sessions`).
- [x] **P6 — Reminders** (create with preset durations, `UNUserNotificationCenter`
      local notifications, history + status badges).
- [x] **P7 — Notes/Memos** (save-to-memory via Hermes, local index, forget).
- [x] **P8 — Goals** (category chips, add goal, list summary markdown).
- [x] **P9 — Reflection** (cadence OFF/WEEKLY/MONTHLY, reflect-now, snooze).
- [x] **P10 — Tasks** (local to-do: add/toggle/remove, open/done sections).
- [x] **P11 — Knowledge graph** (deterministic force-directed viz on SwiftUI Canvas;
      pan/pinch-zoom; node tap → snippets; rebuild from chat records; offline fallback).
- [x] **P12 — Composer** (floating, "+" attachment dock, hold-to-record voice).
      *Polish pending: macOS-dock magnify on hold-slide; real photo/file pickers.*
- [x] **P13 — Voice** (on-device STT via Speech framework — see substitution note).
- [ ] **P14 — Task run** (agent runs, live tool-use previews, human-in-the-loop
      approval card, documents + findings). *Pending — needs a RunEvent Flow bridge.*
- [x] **P15 — Skills** (gallery from `/v1/skills`, search, category grouping).
- [x] **P16 — Support / crisis** 🔒 (consent-first; autonomous contacting DISABLED;
      trusted contacts w/ explicit consent; resources; user-tapped call/text).
- [x] **P17 — Settings + Appearance + About** (Hermes address/disconnect;
      Dark Hermes-Teal / Light warm-paper / System; version).
- [x] **P18 — Navigation shell** (drawer: New chat, recent history, Dashboard,
      History, Knowledge, Goals, Tasks, Memos, Reminders, Reflection, Skills,
      Support, Settings).
- [x] **P19 — Theme** (Hermes Teal dark + warm-paper light; `HermesText`
      display labels; small radii).

## iOS-native substitutions (documented deviations)

- **Voice STT**: Android bundles offline **Vosk**. iOS uses on-device **Speech
  framework** (`SFSpeechRecognizer`, `requiresOnDeviceRecognition = true`) —
  keeps audio on-device, no third-party cloud, no bundled model to ship.
  Tradeoff: on-device recognition availability varies by locale/device; falls
  back to a clear "voice unavailable" message (never silent), matching Android's
  always-show-feedback behavior. *(Alternative — building Vosk for iOS — kept as
  a future option if true offline parity across all locales is required.)*
- **Reminders delivery**: Android WorkManager periodic poll + `AlarmManager`
  exact wake. iOS uses `UNUserNotificationCenter` local notifications scheduled
  at reminder fire time, plus a foreground poll of `/api/jobs` on app open. The
  shared `HermesReminderPoller` / `ReminderPolling.dueNow` logic is reused
  verbatim. iOS background execution is best-effort (BGAppRefresh).

## 🔒 Review-required gates (must stay behind flags, not "done")

1. **Credential + session-key storage** — Keychain only; never logged/plaintext.
2. **Crisis handling** — consent-first; autonomous contacting **built disabled**.
3. **Backend trust boundary** — only the user-configured Hermes; no default/hidden
   backend.
