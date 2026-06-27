# iosApp — SwiftUI front-end

Native SwiftUI UI over the shared KMP `:shared` module. The business logic
(models, `LocalStore`, `ReminderService`) is 100% shared Kotlin; only the UI and
the `UNUserNotificationCenter` scheduler are Swift.

> ⚠️ **This target cannot be built in the Linux dev sandbox** — it requires
> **macOS + Xcode**. The Swift sources and Kotlin/Native `iosMain` code compile
> cleanly (the shared iOS klibs build on Linux), but linking the framework and
> running the app must be done on your Mac. Nothing here has been run on a
> simulator/device yet.

## Files

| File | Role |
|------|------|
| `iosApp/iOSApp.swift` | App entry; requests notification permission |
| `iosApp/ContentView.swift` | TabView: Notes / Reminders / Plan (SwiftUI) |
| `iosApp/AppModel.swift` | `ObservableObject` bridging shared Kotlin → SwiftUI (awaits Kotlin `suspend` funcs as Swift `async`) |
| `iosApp/IosReminderScheduler.swift` | Implements the shared `ReminderScheduler` protocol via `UNUserNotificationCenter` |
| `iosApp/IosEmbedder.swift` | On-device text embeddings (Step 2) via Apple NaturalLanguage; implements the `IosNativeEmbedder` seam |
| `iosApp/Info.plist` | App metadata |
| `project.yml` | XcodeGen spec that generates `iosApp.xcodeproj` |

## On-device embeddings (Step 2)

Semantic memory recall needs each memory turned into a vector. iOS does this
**fully on-device with no network and nothing to ship in the app bundle**, using
Apple's **NaturalLanguage** framework: `NLEmbedding.sentenceEmbedding(for:)`.

| Decision | Choice | Why |
|----------|--------|-----|
| Model | Apple NaturalLanguage `NLEmbedding` | Built into iOS — no model binary to download, ship, or keep out of git. On-device, no network. |
| Dimension | Apple-defined (typically **512**) | Android uses all-MiniLM-L6-v2 (**384**). They differ — **acceptable**: the vector index is built/queried per-device, so vectors are never compared across platforms. |
| Normalization | L2-normalized in `IosEmbedder` | Lets the memory layer use plain dot-product as cosine similarity. |
| Fallback | Deterministic FNV-1a bag-of-words | If `NLEmbedding` is unavailable or returns no vector for a string, we still return a stable, correctly-sized vector (never empty). |

**No Core ML model is shipped**, so there is no large binary to provision or
gitignore. If a future revision switches to a converted **all-MiniLM-L6-v2 Core
ML** model (384-dim, to match Android exactly), that `.mlmodelc`/`.mlpackage`
would be generated via `coremltools` and **must not be committed** — the repo
`.gitignore` already excludes `*.mlmodel` / `*.mlmodelc` / `*.mlpackage`, and the
provisioning steps would be documented here.

### Bridge shape

`Embedder` (shared, `suspend fun embed`) is the contract `MemoryService` uses.
Because having **Swift implement a Kotlin `suspend` function** is the fragile
corner of KMP interop, the Swift side instead implements a *synchronous* Kotlin
seam, `IosNativeEmbedder`, and the Kotlin `IosEmbedderAdapter` adapts it to the
`suspend` `Embedder` (running the work on `Dispatchers.Default`). This mirrors
Step 1's "plain interface, injected from Swift" reminder-scheduler pattern.
`IosFactories.createEmbedder(native:)` performs the wiring; `AppModel` constructs
`IosEmbedder()` and holds the resulting `Embedder` ready for `MemoryService`.

> ⚠️ **Verification gate:** none of the Step 2 iOS code has been compiled — there
> is no macOS/Xcode in the dev sandbox. The **first Xcode build on your Mac is
> the real verification** of `IosEmbedder.swift`, the `KotlinFloatArray` interop,
> and the `IosNativeEmbedder` conformance.

## Build & run (on a Mac)

```bash
# 1. Generate the Xcode project from project.yml
brew install xcodegen        # one-time
cd iosApp
xcodegen generate

# 2. Open and run
open iosApp.xcodeproj
#    Select an iPhone simulator and Run (⌘R).
#    The "Build shared KMP framework" pre-build phase runs
#    ./gradlew :shared:embedAndSignAppleFrameworkForXcode automatically.
```

Prefer not to use XcodeGen? Create a new SwiftUI App target in Xcode, add the
four `iosApp/*.swift` files, then add a Run Script build phase that runs
`./gradlew :shared:embedAndSignAppleFrameworkForXcode` and set
`FRAMEWORK_SEARCH_PATHS` to
`$(SRCROOT)/../shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)`.

## Acceptance check (Notes + a reminder that fires)

1. Run on a simulator; grant the notification prompt.
2. Notes tab → add a note → confirm it appears (and survives an app relaunch —
   persistence is the shared `PersistentLocalStore` over `NSUserDefaults`).
3. Reminders tab → title + "1 min" → **Set reminder**.
4. Background the app; ~1 min later the local notification fires. ✅
