# iosApp — SwiftUI front-end at feature parity with Android

The iOS app is a **SwiftUI client built on the shared Kotlin Multiplatform module**
(`:shared`) — the same business logic the Android app uses (HermesClient, encrypted
stores, chat persistence, knowledge graph, reminder logic, crisis safety). SwiftUI
screens are built on top; the Kotlin core is reused, not reimplemented.

> Requires **macOS + Xcode** to build. The build embeds the KMP `Shared.framework`
> (iosArm64 for device, iosX64 for the Intel-Mac simulator) via a Gradle pre-build
> phase — it cannot be built in the Linux dev sandbox.

## Architecture

| Layer | What |
|-------|------|
| `Shared.framework` (Kotlin/Native) | HermesClient, `HermesConfigStore`, `ChatStore`, `MemoStore`, `TaskStore`, reminder history/polling, `KnowledgeGraphService`, `ReflectionStore`, crisis safety, `EncryptedKeyValueStorage` — reused verbatim from `:shared`. |
| `shared/iosMain/.../ios/LifeAgentIos.kt` | The Swift-facing **facade**: no-default-arg factories for every store, and `suspend fun … (on…: (T)->Unit)` wrappers that turn Kotlin `Flow`s (chat stream, run events, reminder poll) into the interop-safe "Swift calls Kotlin" direction. Enum comparisons (crisis level, reminder status, reflection cadence) stay in Kotlin. |
| Swift `AppEnvironment` | The DI container (mirrors Android `AppContainer`): builds the 🔒 hardware-backed crypto provider (`IosSecretKeyStore` → Keychain + Secure Enclave + CryptoKit) and every encrypted store through the facade. |
| SwiftUI screens | Connect, Dashboard, Chat, History, Notes, Tasks, Reminders, Goals, Reflection, Knowledge graph, Skills, Support, Run-a-task, Settings, and the drawer shell. |

## Interop rules (Kotlin/Native ⇄ Swift, no SKIE)

- `suspend fun` → Swift `async throws` (bridges natively).
- Kotlin `Flow` → a `suspend fun` + Swift closure in `LifeAgentIos` (Swift never
  consumes a Flow directly).
- Kotlin default args don't cross the bridge → the facade fills them in.
- `import Shared` brings Kotlin's `tasks.Task` into scope, shadowing Swift's
  concurrency `Task`; launch async work with `_Concurrency.Task { … }`.
- Kotlin/Native renames some bridged members (`ensureKey` → `ensureKey_`, the
  `aad` label → `aad_`) — the conformances in `IosSecretKeyStore.swift` follow that.

## iOS-native substitutions (documented deviations)

- **Voice** (`VoiceRecognizer.swift`): Android bundles offline Vosk; iOS uses the
  on-device **Speech framework** (`SFSpeechRecognizer`, `requiresOnDeviceRecognition`
  where supported). Audio stays on-device; unavailable locales surface a clear
  message (never silent). Building Vosk for iOS is kept as a future option.
- **Reminders** (`ReminderNotifications.swift`): Android WorkManager + AlarmManager;
  iOS schedules a `UNUserNotificationCenter` local notification at the fire time and
  reuses the shared `HermesReminderPoller` for the foreground poll.

## 🔒 Review-required gates (behind flags; not "done")

1. Credential + session-key storage — Keychain / Secure Enclave only.
2. Crisis handling — consent-first; autonomous contacting **built disabled**.
3. Backend trust boundary — only the user-configured Hermes; no default backend.

## Build & run (on a Mac)

```bash
brew install xcodegen           # one-time
cd iosApp
xcodegen generate               # re-run whenever .swift files are added/removed
# Debug (fast — reuses the cached debug framework):
xcodebuild -project iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination "generic/platform=iOS" -archivePath build/iosApp.xcarchive archive \
  CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO
# Package an unsigned .ipa (sideload with a free Apple ID via AltStore/Sideloadly):
#   zip the .app in build/iosApp.xcarchive/Products/Applications into Payload/.
```

The pre-build phase runs `./gradlew :shared:embedAndSignAppleFrameworkForXcode`,
which builds + embeds `Shared.framework`. It needs `JAVA_HOME` (the phase sets it);
the shared `android {}` target means the Android SDK must also be present (`sdk.dir`
in `local.properties`).

See `docs/IOS_PARITY.md` for the per-screen parity checklist.
