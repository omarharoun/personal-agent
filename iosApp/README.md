# iosApp — SwiftUI front-end

> ⚠️ **This target cannot be built in the Linux dev sandbox** — it requires
> **macOS + Xcode**. Everything here is written but UNBUILT/UNRUN on iOS.

## Hermes Life Agent client (current direction)

The app is being repurposed into a **Hermes Agent client** (same as Android). The
iOS Hermes client is a pure-Swift starting point (no Kotlin-Flow interop needed):

| File | Role |
|------|------|
| `iosApp/HermesClient.swift` | URLSession client — `/health`, streaming `/v1/chat/completions` (SSE), `/api/jobs`. Mirrors the shared Kotlin `HermesClient` and the wire contract verified live in `docs/PHASE0–2`. |
| `iosApp/HermesLifeAgentView.swift` | SwiftUI **Connect** (base URL + key, tested via `/health`) + streaming **Chat**, with 🔒 **Keychain**-backed credential + session-key storage. |

To adopt it, point `iOSApp.swift`'s root at `HermesLifeAgentView()`. Feature parity
with Android (reminder polling, goals, reflection, the consent-first crisis card)
is the remaining iOS work; the shared KMP core (`HermesClient`, `HermesConfig`,
jobs, reflection, `LifePrompts`) is already there, and the Swift `HermesClient`
covers chat + reminders today.

---

## Legacy (on-device) SwiftUI — being retired

The files below are the previous on-device build (Notes/Reminders over shared
Kotlin + on-device ML). They remain for reference while iOS is migrated to the
Hermes client; the Android app has already retired this stack.

## Files

| File | Role |
|------|------|
| `iosApp/iOSApp.swift` | App entry; requests notification permission |
| `iosApp/ContentView.swift` | TabView: Notes / Reminders / Plan (SwiftUI) |
| `iosApp/AppModel.swift` | `ObservableObject` bridging shared Kotlin → SwiftUI (awaits Kotlin `suspend` funcs as Swift `async`) |
| `iosApp/IosReminderScheduler.swift` | Implements the shared `ReminderScheduler` protocol via `UNUserNotificationCenter` |
| `iosApp/IosEmbedder.swift` | On-device text embeddings (Step 2) via Apple NaturalLanguage; implements the `IosNativeEmbedder` seam |
| `iosApp/IosOnDeviceLlm.swift` | On-device LLM (Step 3) via **MLX Swift**; implements the `IosNativeLlm` seam |
| `iosApp/LlmModelProvisioner.swift` | Locates / provisions the LLM weights in Application Support; backs `isAvailable` |
| `iosApp/Info.plist` | App metadata |
| `project.yml` | XcodeGen spec that generates `iosApp.xcodeproj` (also adds the MLX SwiftPM package) |

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

## On-device LLM (Step 3)

A **small quantized LLM runs fully on-device, no network at inference**, exposed
through the shared `OnDeviceLlm` contract:

```kotlin
data class GenOptions(val maxTokens: Int = 512, val temperature: Float = 0.7f, val stop: List<String> = emptyList())
interface OnDeviceLlm {
    val isAvailable: Boolean
    suspend fun generate(prompt: String, options: GenOptions = GenOptions()): String
    fun generateStream(prompt: String, options: GenOptions = GenOptions()): Flow<String>
}
```

### Runtime & model — and why

| Decision | Choice | Why |
|----------|--------|-----|
| Runtime | **MLX Swift** (`mlx-swift-examples`: `MLXLLM` + `MLXLMCommon`) | Apple-silicon native (Metal + unified memory) → best perf/Watt on iPhone/iPad; Swift-first API maps cleanly to our seam + token callback; loads `mlx-community` quantized models straight from disk. |
| Model | **Llama 3.2 3B Instruct, 4-bit** (`mlx-community/Llama-3.2-3B-Instruct-4bit`, ~1.8 GB) | Strong small instruct model in MLX format. Runtime RAM ≈ 2.5–3 GB → comfortable on A16/A17/M-series, tight on older devices. Swap `LlmModelProvisioner.modelRepoId` to the **1B** variant (~0.7 GB) for low-memory devices, or a **Qwen 2.5 4-bit** repo. |
| Alternative considered | llama.cpp via SwiftPM | Runs on more targets (incl. x86_64 sim), but costs a C/C++ bridge to maintain and a less-optimal Metal path. For an Apple-only product, MLX is the better fit. |

> ⚠️ **MLX is Apple-silicon (arm64) only.** It builds/runs on real devices and the
> **arm64** iOS simulator — **not** the legacy x86_64 simulator. Use an
> Apple-silicon Mac.

### Bridge shape (same pattern as Steps 1/2)

`OnDeviceLlm` is `suspend` + returns a `Flow`. Because having **Swift implement a
Kotlin `suspend` function or produce a `Flow`** is the fragile corner of KMP
interop, the Swift side implements a *synchronous* seam, `IosNativeLlm`:

- `generate(...) -> String` — blocking full generation.
- `generateStream(..., onToken: (String) -> Void)` — blocking; **calls** a Kotlin
  closure per text chunk (the supported interop direction is Swift *calling*
  Kotlin, not implementing its `Flow`).

The Kotlin `IosLlmAdapter` lifts this to the shared contract on
`Dispatchers.Default`: `generate` via `withContext`, `generateStream` via a
`channelFlow` that feeds `onToken` chunks into the flow with back-pressure.
`IosFactories.createOnDeviceLlm(native:)` does the wiring; `AppModel` constructs
`IosOnDeviceLlm()` and exposes `askLocalModel(_:)`. (The raw Kotlin `Flow` is
consumed by **shared** Kotlin code, not from Swift — deliberately avoiding the
fragile Flow-from-Swift consumption.)

`IosOnDeviceLlm` blocks its (background-thread) seam calls on an internal
`async` MLX task via a semaphore — safe because the adapter only ever calls it on
`Dispatchers.Default`, never the main thread.

### Model provisioning (out of git)

Weights are **never committed** (`.gitignore` excludes `*.safetensors`, `*.gguf`,
`models/`). They live in the app's **Application Support** directory
(`…/Application Support/models/Llama-3.2-3B-Instruct-4bit`), and `isAvailable`
is a cheap file-existence check there (`LlmModelProvisioner.isAvailable`). Two
supported provisioning paths:

1. **Download-on-first-run** (default): fetch the quantized files from the
   `mlx-community` Hugging Face repo into Application Support. This is the **only**
   moment the network is touched — explicit and user-triggerable, never part of
   inference. Mark the directory "exclude from backup".
2. **Documented manual drop** (air-gapped builds): copy a local model folder
   (`config.json`, `tokenizer.json`, `*.safetensors`) into that directory. On a
   Mac:
   ```bash
   # one-time, with the huggingface CLI
   huggingface-cli download mlx-community/Llama-3.2-3B-Instruct-4bit \
     --local-dir "$HOME/Library/Containers/com.personalagent.ios/Data/Library/Application Support/models/Llama-3.2-3B-Instruct-4bit"
   ```

Until weights are present, `isAvailable == false` and `generate` throws / the UI
shows "model not installed yet" — the rest of the app is unaffected.

> ⚠️ **Verification gate:** none of the Step 3 iOS code has been compiled (no
> macOS/Xcode here). The **first Xcode build + run on your Apple-silicon Mac/device
> is the real verification** of `IosOnDeviceLlm.swift`, the MLX SwiftPM resolution
> + API surface (`LLMModelFactory` / `ModelContainer` / `generate` may need a tag
> bump), the `IosNativeLlm` conformance, and the `[String]` / `(String) -> Void`
> bridging. Before the MLX package is added, `#if canImport(MLXLLM)` keeps the app
> building with the LLM reporting unavailable.

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
