# Personal Agent

A private, on-device personal AI agent for **iPhone and Android**, built on a
shared **Kotlin Multiplatform (KMP)** core with **native UI per platform**
(Jetpack Compose on Android, SwiftUI on iOS).

> **Status: Step 1 of 7 — thin running foundation, NO AI yet.**
> This is the clean scaffold + stable interfaces that later steps slot into.
> Memory/AI, cloud, encryption, and crisis features are later, gated steps and
> are **not** built here. Data is stored **unencrypted behind a swap-in
> interface** for now (encryption is Step 5 — see
> [`docs/SECURITY_REVIEW.md`](docs/SECURITY_REVIEW.md)). Do not put real user
> data in it yet.

## UI-stack choice & why

**Decision: native UI per platform (Jetpack Compose + SwiftUI) over a shared KMP
business-logic module** — *not* Compose Multiplatform.

- The brief calls for "native UI per platform". Reminders — the one feature in
  Step 1 with real OS integration — are inherently platform code anyway
  (`AlarmManager` vs `UNUserNotificationCenter`), so a shared UI layer would buy
  little here while adding abstraction.
- SwiftUI + Compose give the most faithful native notification/permission UX and
  let each platform evolve idiomatically.
- **All logic that isn't pixels or OS calls lives in `:shared`** (models, store,
  reminder service), so the platforms stay thin and the logic is unit-tested once.

## Project structure

```
personal-agent/
├── settings.gradle.kts          # includes :shared, :androidApp (iosApp is Xcode, not Gradle)
├── gradle/libs.versions.toml     # single source of dependency versions
├── shared/                       # KMP module — the shared brain
│   └── src/
│       ├── commonMain/…/model    # Note, Reminder, PlanItem, MemoryEntry
│       ├── commonMain/…/store    # LocalStore interface + PersistentLocalStore + KeyValueStorage (Step-5 seam)
│       ├── commonMain/…/reminder # ReminderScheduler interface + ReminderService (pure logic)
│       ├── commonMain/…/util     # Clock / SystemClock
│       ├── commonTest            # 19 unit tests (models, store, reminder logic)
│       ├── androidMain           # SharedPreferences storage, system clock  (plaintext placeholder)
│       ├── iosMain               # NSUserDefaults storage, system clock, Swift factories (plaintext placeholder)
│       └── jvmMain               # File-backed storage (for desktop/CI test runs)
├── androidApp/                   # Jetpack Compose app (Notes/Reminders/Plan + AlarmManager firing)
├── iosApp/                       # SwiftUI app (see iosApp/README.md) — built on macOS
└── docs/SECURITY_REVIEW.md       # the two 🔒 human-review gates (encryption, crisis)
```

## Core data models (`shared/.../model`)

| Model | Purpose |
|-------|---------|
| `Note` | free-form user note (title/body, timestamps) |
| `Reminder` | time-based reminder with `triggerAtMillis` + `ReminderStatus` |
| `PlanItem` | a planning/agenda item (done flag, order, optional due) |
| `MemoryEntry` | typed unit of agent memory; `embedding` field reserved for a **deferred** on-device model |

## Storage — `LocalStore` and the encryption seam

Callers depend only on the `LocalStore` interface. `PersistentLocalStore`
serializes entities to JSON and writes them through a **`KeyValueStorage`** — the
single seam where encryption lands in Step 5.

```
UI / ViewModel ─► LocalStore (interface)
                     └─ PersistentLocalStore (JSON)
                          └─ KeyValueStorage  ◄── 🔒 Step 5 swaps THIS for an encrypted wallet
                               ├─ AndroidKeyValueStorage  (SharedPreferences, plaintext — placeholder)
                               ├─ IosKeyValueStorage      (NSUserDefaults,   plaintext — placeholder)
                               └─ FileKeyValueStorage     (JVM file,         plaintext — placeholder)
```

Every placeholder is marked `// TODO Step 5: swap for encrypted wallet`.

## Reminder firing (per platform)

The shared `ReminderService` validates + persists; a platform `ReminderScheduler`
turns it into a real OS alarm.

- **Android:** `AndroidReminderScheduler` → `AlarmManager.setExactAndAllowWhileIdle`
  → `ReminderReceiver` (BroadcastReceiver) posts a notification and marks the
  reminder `FIRED`. `BootReceiver` re-arms reminders after reboot.
  Permissions: `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`.
- **iOS:** `IosReminderScheduler` (Swift, implements the shared protocol) →
  `UNUserNotificationCenter` with a `UNTimeIntervalNotificationTrigger`.

## Build order

1. `:shared` — the brain (builds + unit-tested first).
2. `:androidApp` — depends on `:shared`.
3. `iosApp` — depends on `:shared`'s framework; built with Xcode on macOS.

## How to build each platform

Requires a **JDK** (not just a JRE) and, for Android, the **Android SDK**
(`local.properties` → `sdk.dir`, or `ANDROID_HOME`).

```bash
# Shared business logic + all unit tests (runs anywhere, no device needed)
./gradlew :shared:jvmTest

# Whole shared module (Android AAR + iOS klibs + metadata)
./gradlew :shared:assemble

# Android debug APK
./gradlew :androidApp:assembleDebug
#   → androidApp/build/outputs/apk/debug/androidApp-debug.apk

# iOS — on macOS only (see iosApp/README.md)
cd iosApp && xcodegen generate && open iosApp.xcodeproj
```

## What is verified vs what needs your machine

Built & tested in a Linux dev sandbox (Gradle 9.6.1 on a JDK, Android SDK 36):

| Target | Result |
|--------|--------|
| `:shared` JVM unit tests | ✅ **19/19 pass** (models, store CRUD/persistence, reminder logic) |
| `:shared` Android AAR | ✅ compiles |
| `:shared` iOS klibs (arm64/x64/simulator) | ✅ Kotlin/Native compiles (framework *linking* needs macOS) |
| `:androidApp` debug APK | ✅ builds |
| **iOS app (SwiftUI) build/run** | ⚠️ **needs your Mac + Xcode** — Swift not compiled here |
| Android reminder fires on a device | ⚠️ needs an emulator/device (code complete) |
| iOS reminder fires on a device | ⚠️ needs your Mac + simulator/device |

### Acceptance check (add a note + a reminder, reminder fires)

- **Logic:** ✅ covered by `ReminderServiceTest` / `PersistentLocalStoreTest`.
- **Android on-device:** code complete; run `:androidApp` on an emulator, add a
  note, set a 1-minute reminder, background the app → notification fires. *(Not
  executed here — no emulator/device in the sandbox.)*
- **iOS on-device:** code complete; verify on your Mac per `iosApp/README.md`.

## Deferred to later steps (NOT decided here)

Flagged so they aren't mistaken for Step-1 work:

- **On-device model / embeddings / vector DB** ("tech-from-measurement") —
  `MemoryEntry.embedding` is a reserved nullable field; the model + index choice
  is **deferred**.
- **Encryption at rest / key management** — Step 5 (🔒 gate 1).
- **Memory / AI, cloud sync** — later steps.
- **Crisis autonomous action (e.g. placing a call)** — Step 7 (🔒 gate 2), not
  present in this codebase.

## Toolchain notes (this sandbox)

- Gradle **9.6.1** runs on a JDK; Kotlin **2.4.0**, AGP **9.2.1**, compileSdk **36**, minSdk **26**.
- AGP 9 specifics that shaped the build: the KMP library uses
  `com.android.kotlin.multiplatform.library` (the classic `com.android.library`
  is incompatible with the KMP plugin), and the Android app relies on AGP's
  **built-in Kotlin** (no separate `kotlin.android` plugin).
