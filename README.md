# Life Agent — a Hermes Agent client

A polished **iOS + Android** client for a **user-owned [Hermes Agent](https://hermes-agent.nousresearch.com)**
backend, specialized for one job: **helping you be better at your life** — chat
with memory, notes, reminders, goals, and gentle reflection. Built on a shared
**Kotlin Multiplatform (KMP)** core (Ktor + Jetpack Compose on Android, SwiftUI
on iOS).

**Path A — bring-your-own-Hermes.** You run your own Hermes (on your computer or
a small server); the app is a thin, trusted client to it. **All memory and data
live on your server. The app stores no sensitive user content** — only the
connection config (URL, key) and a memory-scope key, sealed on-device.

> **Status: v1 complete — all 5 phases, plus Phase 6 (Learning Guide).** Connect +
> streaming chat (Phase 1), notes + reminders (Phase 2), goals + crisis handling
> (Phase 3), reflection (Phase 4), polish + production slim (Phase 5), and a
> memory-grounded **Learning Guide** over your Hermes' web tools (Phase 6 — see
> *What's new in v2.5.0*). The app is a thin Hermes client (the on-device ML stack
> is retired — Hermes is the brain). A first-run **setup guide** walks you through
> pointing it at your Hermes. See the per-phase notes in [`docs/`](docs/).
>
> ⚠️ Three 🔒 areas are **built and flagged, not shippable** until human review:
> credential/session-key storage, crisis handling (crisis-expert review; resources
> are placeholders to verify/localize; autonomous contact is built **disabled**),
> and the trust boundary. Search the code for `REVIEW REQUIRED`.
>
> ⚠️ Crisis handling is 🔒 built-and-flagged, **not shippable** until a
> crisis-response expert reviews it (recognizer is coarse; resources are
> placeholders to verify/localize; any autonomous contact is built **disabled**).
>
> Three 🔒 safety-critical areas (credential/session-key storage, crisis
> handling, trust boundary) are built and flagged `// REVIEW REQUIRED` in code —
> **a human must review them before real users rely on them.** See
> [`docs/SECURITY_REVIEW.md`](docs/SECURITY_REVIEW.md) and the per-phase notes in
> [`docs/PHASE0.md`](docs/PHASE0.md), [`docs/PHASE1.md`](docs/PHASE1.md).

## What's new in v2.5.0 — Learning Guide (Phase 6)

**A personal, memory-grounded "skill-up" guide, on both platforms.** Tell your
agent what you want to get better at; it uses *its* accumulated memory of you plus
*your Hermes'* built-in web search/browse tools to point you at the next right
thing to learn from the **free, open web** — then remembers what it suggested and
how it landed, and adapts.

- **Declare a goal** (Learning drawer entry) — topic + why, your starting level
  (asked once), and how you like to learn (only if you volunteer it). Goals live
  in the authoritative device-local `LearningStore`; a compact "current focus" is
  mirrored to Hermes memory.
- **What's next?** — for a goal, the agent runs `web_search`/`web_extract` and
  returns **1–3 concrete free resources** (a specific video/doc/course page, never
  a listicle), each with one honest sentence of *why this, for you, now*, filtered
  against your level/style and what you've already seen/finished/abandoned.
- **Close the loop** — one tap marks a resource started / finished / abandoned /
  loved / not-for-me. The agent adapts: abandon a concept twice → it's approached
  differently; finish fast → it steps up; prefer video → video is weighted.
- **Woven in, not bolted on** — a quiet learning touch appears in the Phase-4
  reflection only when something's in progress, and the Goals screen links to the
  guide. Same one-tap, quiet-if-ignored ethos as the rest of the app.

**Hard boundaries (enforced):** guide-to-open-web only — the app **never
scrapes, re-hosts, or stores third-party content**; it keeps only your own state
plus the link/title/one-line rationale. **No new backend** — everything runs
through *your* Hermes (its web tools, its memory); the app stays a thin client. If
your Hermes has no web-search backend configured, the guide says so (checked via
`GET /v1/toolsets`) instead of failing silently.

**🔒 REVIEW REQUIRED (built + flagged, not shipped):** (1) fetched web content is
**untrusted** — the recommendation prompt tells the agent to treat page text as
data, never as commands (Hermes also wraps tool output in
`<untrusted_tool_result>`); the app parses/render titles/URLs/summaries as **inert
text**. (2) Links open **only** in the system browser (Android `ACTION_VIEW`, iOS
`UIApplication.open`) — **no in-app WebView** of arbitrary HTML. Search the code
for `REVIEW REQUIRED`.

Shared logic lives in `:shared` (`learning/` + `hermes/LearningPrompts.kt`); the
Android Compose `LearningScreen` and iOS SwiftUI `LearningView` are thin UIs over
it.

## What's new in v2.4.0

**Voice now uses our OWN bundled offline engine — works on any Android phone.**

- Dropped the dependency on the phone's Google offline speech pack (and
  `SpeechRecognizer`/`EXTRA_PREFER_OFFLINE`) for transcription. Voice is now backed
  by **[Vosk](https://github.com/alphacep/vosk-api) (Apache-2.0)**, bundled in the
  APK as a native engine (`libvosk.so`). Chosen over whisper.cpp/whisper.tflite
  because the Vosk AAR ships a ready-to-use streaming recognizer over `AudioRecord`
  (no NDK build, no manual audio loop) — lower integration risk for a solid offline
  result. (See [ATTRIBUTION.md](ATTRIBUTION.md).)
- **Fully offline, fully private:** all audio capture + transcription happen on the
  device. Nothing is sent to Google or any cloud. The only network touch is a
  **one-time ~40 MB model download** (`vosk-model-small-en-us-0.15`) on first voice
  use, cached in app-private storage — the base APK is not bloated by it. The
  "model not downloaded yet" state shows a **progress indicator** (`Setting up
  offline voice… NN%`), never a silent no-op.
- **Record → transcribe → send path:** press mic (touch-driven red-dot + timer
  feedback shows instantly) → Vosk `SpeechService` opens `AudioRecord` at 16 kHz and
  streams PCM to the recognizer → live partial transcript appears → release stops the
  mic and flushes the **final transcript**, which is sent to Hermes exactly like typed
  text. Slide away to cancel; `RECORD_AUDIO` is requested on first use; every failure
  path surfaces a clear message.
- APK grows from ~22 MB to **~39.5 MB** (native `libvosk.so` for arm64-v8a +
  armeabi-v7a, the two ABIs that cover essentially all real phones). The language
  model stays out of the APK (downloaded on demand).

## What's new in v2.3.1

- **Voice hold gives instant feedback** — the recording indicator (red dot + `m:ss`
  timer) is now driven **purely by the touch state**, set the instant your finger
  presses the mic, fully decoupled from the `SpeechRecognizer`. In v2.3.0 the
  indicator was gated on the recognizer's `listening` flag, so on the first hold
  (permission not yet granted) or when the offline recognizer errored instantly, it
  never appeared — the hold looked completely dead. The press handler also uses
  `requireUnconsumed = false` + `down.consume()` so nothing upstream can swallow the
  press, and a `try/finally` guarantees the release fires (and the indicator clears)
  even if the permission dialog steals the pointer. Transcription is still on-device
  only; if it can't transcribe, you still see the indicator on hold and a clear
  message on release — never a silent no-op.

## What's new in v2.3.0

Fixes to two on-device composer bugs reported from the phone (Android only):

- **Attachment dock (`+`) fixed** — the three options (Camera / Photo / File) now
  render as a compact vertical column that grows straight **up from just above the
  `+` button** (bottom-anchored transition), fully on-screen. Previously the default
  reveal animation made them read as a clipped, half-off-screen left-edge drawer.
  Tap the `+` to open (stays open, tap an option), or **press-and-hold and slide up**
  to the macOS-Dock magnify; release on the highlighted option to trigger it.
- **Voice fixed & never silent** — hold the mic (empty field) to record; a live
  **recording indicator** (red dot + `m:ss` elapsed + partial transcript) shows while
  listening; release sends the transcript, slide away cancels. First hold now requests
  `RECORD_AUDIO` and, once granted, prompts you to hold again (avoids the old
  record-after-release race). Any failure — permission denied, no on-device speech
  pack, recognizer unavailable — now surfaces a clear message instead of doing nothing.
  Transcription stays **on-device** (system `SpeechRecognizer`, `EXTRA_PREFER_OFFLINE`):
  no audio and no third-party/cloud speech service — only the resulting text goes to
  your Hermes. *If a device has no offline speech pack, the app tells you to enable it
  in system settings rather than falling back to any cloud recognizer.*
- **Floating composer (really this time)** — removed the pill surface/border/shadow
  that was still drawn behind the text; the field now floats directly over the page
  (the `+` and send controls keep their own circular backgrounds for legibility).

## What's new in v2.2.0

UX pass on the chat surface plus the first iOS CI build:

- **Floating composer** — the input pill floats over the transcript (no opaque
  band under it); messages scroll behind it, messenger-style.
- **Attachment dock (`+`)** — a left `+` with three options (Camera / Photo /
  File). Tap to open, or **press-and-hold and slide up** to a macOS-Dock-style
  stack that magnifies toward your finger; release on an option to pick it.
- **Hold-to-talk voice** — with an empty field, hold the mic to record; release
  to send, slide away to cancel. Transcription is done **on-device** by the
  system speech recognizer — no audio leaves the phone; only the text is sent.
- **Select & copy** — chat text is selectable/copyable; every assistant reply has
  **Copy** / **Save** (share-sheet) so any document it writes can leave the app.
- **History multi-select** — a **Select** mode with a red **Delete** to remove
  several conversations at once (long-press a row to start selecting).
- **Scheduling that delivers in-app** — when you ask the agent to schedule/
  automate something, the client injects a *system* steer so it uses **local,
  in-app delivery** and never `send()`/push/email/external channels (the fix for
  the "scheduled task never arrived" failure). Jobs the app creates are already
  `deliver=local`. *Note: fully surfacing a recurring task's generated output
  in-app depends on the live Hermes `/api/jobs` run-output shape and still wants
  a live-instance check per Phase 0.*
- **Cleaner Knowledge Map** — the extraction prompt now demands concrete
  real-world subjects and filters vague/meta labels ("Memory Test",
  "Self-Knowledge", "Admit Uncertainty", …).
- **Settings** — removed the "First name" field (the agent recalls your name from
  memory instead).

### iOS build via GitHub Actions (manual)

`.github/workflows/ios-build.yml` builds an **unsigned `.ipa`** so you can
sideload onto your own iPhone with a **free Apple ID** (AltStore / Sideloadly
re-sign it) — no paid Apple Developer account, no signing secrets in CI. It is
**manual-only** (`workflow_dispatch`), never on push. Run it from the **Actions**
tab → *iOS build (unsigned IPA)* → *Run workflow*, or `gh workflow run
"iOS build (unsigned IPA)"`, then download the `hermes-life-agent-unsigned-ipa`
artifact.

## Design

The UI adopts the **Hermes Agent web dashboard's "Hermes Teal" look** so the Life
Agent feels like a natural extension of Hermes: a dark-teal canvas (`#041C1C`)
with warm-cream text/accent (`#FFE6CB`), thin cream-tinted card borders, small
(8px) radii, cream-filled buttons, and the signature UPPERCASE wide-tracked
"display" labels + monospace metadata. These design tokens are **adapted from the
MIT-licensed** Hermes frontend + `@nous-research/ui` (© 2025 Nous Research),
re-implemented natively in Compose — no Hermes code/CSS/fonts/logo are bundled.
See [`ATTRIBUTION.md`](ATTRIBUTION.md). A matching warm-paper light theme is
included.

### Home dashboard

The home is a warm, personal "life OS" board, not a technical feed. It opens with
a time-of-day greeting + the user's name ("Good evening, Omar.") and a date line,
then four live **life-area cards**, each previewing real content and tapping into
its full screen:

| Card | Preview shows | Real source |
|------|---------------|-------------|
| **Goals** | Top 1–3 current goals | The agent's own memory — `POST /v1/chat/completions` with the `LifePrompts.listGoals()` prompt, parsed into bullets. |
| **Tasks** | Up to 3 open to-dos, check off inline | `TaskStore` — a lightweight to-do list kept **locally on the device** (instant, offline; the Tasks screen says so). |
| **Memos** | Latest 2–3 saved notes | `MemoStore` — a local index of notes; the authoritative copy lives in **Hermes memory** (Hermes has no "list my notes" endpoint, so the app mirrors what it saved). |
| **Reminders** | Next few upcoming | `GET /api/jobs` merged with local reminder history (`ReminderHistory`). |

The **greeting name** comes from a locally-stored "Your name" (Settings → *Your
name*); if that's blank the app asks the agent **once** ("what's my first name?"),
accepts the reply only if it's a plausible name, and caches it. If neither is
known it greets with no name — never a fabricated one.

Chat and **Run a task** (the tool-use preview flow) are kept **secondary** — Chat
is a floating action button, Run-a-task lives in the top-bar overflow. The old
raw session-activity feed, the capabilities/toolsets card, and the on-home Skills
gallery were removed; Skills is still reachable from the navigation drawer.

**Caching (instant home, no blocking spinner).** The home paints immediately from
persistent local state and revalidates in the background — never a full "Loading…"
once anything is cached. Tasks, Memos, and Reminders already read straight from
their own persistent local stores (`TaskStore` / `MemoStore` /
`ReminderHistoryStore`), so they render instantly and survive relaunch. The only
networked card, **Goals** (an agent query), is backed by a persistent
**stale-while-revalidate** cache (`HomeCacheStore`, a JSON-over-encrypted-KV
store): the home shows the cached goals right away, then re-asks the agent only
when the cache is older than 6 h **or** the user hits *Refresh* (overflow) — a
subtle spinner in the card header signals a background revalidate; the full
"Loading…" appears only on the first-ever fetch (empty cache). This stops the
Goals card from re-querying the agent on every home appearance.

**Chat composer.** The chat input floats directly over the transcript with no
surface behind it (messenger-style), docks flush above the keyboard (edge-to-edge
insets), grows to a few lines, and has a clear circular send button plus a left
`+` attachment dock and hold-to-talk voice (see *What's new in v2.3.0*).

**🔒 Support.** "Find support" on the Support screen opens a dedicated Support
Resources view (`SupportResourcesScreen`) with gentle guidance, real crisis
resources, and — if the user has added them — their trusted people (to *help
them* reach out; never autonomous). The resource list is still a placeholder
pending crisis-expert verification/localization (`// REVIEW REQUIRED`).

**Kotlin Multiplatform, continuing this repo.** This codebase was already a
working KMP app (Ktor HTTP client, Compose chat UI with streaming, hardware-
sealed secure storage, notes/reminders + local notifications). Repointing that
plumbing at Hermes reuses ~all of it, so KMP is the natural continuation over a
React-Native rewrite. Shared logic (the `HermesClient`, config/session-key store,
wire models) lives in `:shared` and is unit-tested once; each platform keeps a
thin native UI.

### Chat history (persisted on-device)

Conversations are saved on the device as you chat, so they survive an app restart.
Each thread (title, its `X-Hermes-Session-Id`, timestamps) and every message
(role, text, time) is mirrored to a sealed-at-rest **`ChatStore`**
(`shared/.../chat/ChatStore.kt`) — JSON over the same encrypted `KeyValueStorage`
as the app's other local stores, so no database dependency is pulled in. A
**History** screen (drawer → *History*) lists every saved conversation newest-first
with a preview + relative time; tapping one reopens it with its messages and its
original session id, keeping the server's short-term threading continuous. On
launch the app also **best-effort merges** any server-side conversations from
Hermes `GET /api/sessions` that aren't already on the device (marked with a cloud
badge), lazy-loading their transcripts from `GET /api/sessions/{id}/messages` when
opened. Hermes remains the authoritative store; this is the device's own copy for
instant, offline browsing. Clearing local history never touches Hermes memory.

### Knowledge map (derived from your conversations)

A **Knowledge** screen visualizes an interactive node-link graph of the topics,
entities and concepts you've explored — **derived from your saved chat records**,
honestly labeled as such and **not** presented as Hermes memory. See
[`docs/KNOWLEDGE_GRAPH.md`](docs/KNOWLEDGE_GRAPH.md).

## Connecting the app to your Hermes

1. Install + run Hermes with the OpenAI-compatible API server enabled
   (`API_SERVER_ENABLED=true`, `API_SERVER_KEY=<your-key>` in `~/.hermes/.env`),
   then `hermes gateway run`. It listens on `http://127.0.0.1:8642` by default
   (see [`docs/PHASE0.md`](docs/PHASE0.md) for the exact v0.18.0 surface).
2. Launch the app → **Connect** screen → enter the Hermes address (e.g.
   `http://192.168.1.20:8642`) + your API key → **Test & Connect**. The app
   verifies with `GET /health` before saving.
3. Chat. Your agent remembers you across conversations and launches.

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

# Android release App Bundle (R8 minify + resource shrinking)
./gradlew :androidApp:bundleRelease
#   → androidApp/build/outputs/bundle/release/androidApp-release.aab

# iOS — on macOS only (see iosApp/README.md)
cd iosApp && xcodegen generate && open iosApp.xcodeproj
```

## Android release build

The `release` build type is **release-config-ready** (R8 minify + resource
shrinking, AAB output, signing-from-properties). It is **not publishable yet** —
see [`docs/PLAY_RELEASE.md`](docs/PLAY_RELEASE.md) for the honest blocker list
(the three 🔒 reviews, real signing key, device QA, model licensing).

**Signing.** Release signing reads a gitignored `keystore.properties` at the repo
root. Copy the template and drop in your key:

```bash
cp keystore.properties.example keystore.properties   # then edit it
# keystore.properties, *.jks, *.keystore are gitignored — never committed.
```

If `keystore.properties` is absent (CI / this sandbox), the release build falls
back to **debug signing** so it still builds — that AAB is **not uploadable** to
Play. R8 keep-rules for the native/reflective libs (ONNX, MediaPipe, Ktor/OkHttp,
kotlinx-serialization, JNI) live in `androidApp/proguard-rules.pro`; re-verify
them with a device smoke test after any dependency bump.

**No model weights in the AAB.** The on-device LLM (~0.5–2 GB) and embedding model
are provisioned at runtime (download-on-first-run / sideload), never bundled —
the release AAB is ~54 MB and contains only code + native runtime libs.

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

## On-device embeddings (Step 2 — Android)

Semantic memory recall needs to turn text into vectors **on-device, fully
offline**. The Android side implements the shared `Embedder` contract:

```kotlin
// shared/commonMain (com.personalagent.shared.memory) — platform-agnostic contract
interface Embedder { val dimension: Int; suspend fun embed(text: String): FloatArray }
```

**Model + runtime:** [`all-MiniLM-L6-v2`](https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2)
(Sentence-Transformers, 384-dim) exported to ONNX, run with **ONNX Runtime
Mobile** (`com.microsoft.onnxruntime:onnxruntime-android`). Chosen because it is
small (~90 MB fp32), strong general-purpose sentence quality, a stable ONNX
export, and ORT Mobile is a mature, self-contained native runtime with no server
and no Google Play Services dependency. Pipeline: WordPiece tokenize → transformer
→ **mean-pool** over the attention mask → **L2-normalize** (so a dot product is
cosine similarity).

| Piece | File |
|-------|------|
| Contract (owned by `feat/step2-shared`) | `shared/.../memory/Embedder.kt` |
| Implementation | `androidApp/.../embedding/AndroidEmbedder.kt` |
| Tokenizer (pure Kotlin WordPiece) | `androidApp/.../embedding/BertTokenizer.kt` |
| Factory (how the app obtains it) | `androidApp/.../embedding/EmbedderFactory.kt` |
| Wiring | `AppContainer.embedder` / `AppContainer.isEmbeddingModelInstalled` |
| On-device test (self-skips if absent) | `androidApp/src/androidTest/.../AndroidEmbedderTest.kt` |

### Provisioning the model (kept OUT of git)

The ~90 MB weights are **not** committed — `androidApp/src/main/assets/models/`
is gitignored. Fetch the model + vocab into app assets once:

```bash
./gradlew :androidApp:downloadEmbeddingModel
```

This downloads `onnx/model.onnx` and `vocab.txt` from Hugging Face into
`androidApp/src/main/assets/models/all-MiniLM-L6-v2/`. (You can also drop those
two files there by hand.) The `.onnx` asset is kept uncompressed
(`androidResources.noCompress += "onnx"`) so ORT loads it efficiently. Clones
without the asset still build — `EmbedderFactory.isModelInstalled()` reports
`false` and the app can gate semantic features or prompt to download.

## On-device LLM (Step 3 — Android)

Local generation needs a small language model that runs **on-device, fully
offline**. The Android side implements the shared `OnDeviceLlm` contract:

```kotlin
// shared/commonMain (com.personalagent.shared.llm) — platform-agnostic contract
data class GenOptions(val maxTokens: Int = 512, val temperature: Float = 0.7f, val stop: List<String> = emptyList())
interface OnDeviceLlm {
    val isAvailable: Boolean
    suspend fun generate(prompt: String, options: GenOptions = GenOptions()): String
    fun generateStream(prompt: String, options: GenOptions = GenOptions()): Flow<String>
}
```

**Runtime + model:** **MediaPipe LLM Inference API**
(`com.google.mediapipe:tasks-genai`) running **Gemma 3 1B (int4)** as the default
`.task` bundle. Chosen because MediaPipe is a mature, self-contained native
runtime (GPU/CPU, **no** Google Play Services, no server), and Gemma 3 1B int4 is
the smallest credible instruct model (~0.5 GB) so it fits a phone's RAM budget.
**Llama 3.2 3B** is a drop-in higher-quality alternative (larger footprint) — set
a different `.task` filename and push it instead. Per the spec the final pick is a
**measurement decision on the target phone** (latency / RAM / quality); the
default is just a sensible starting point, and the model path is configurable.

Mapping `GenOptions` onto MediaPipe:

| Option | How it's applied |
|--------|------------------|
| `temperature` | set on a fresh `LlmInferenceSession` per request |
| `maxTokens` | enforced client-side (stop after N streamed deltas); the engine is created with a generous KV capacity |
| `stop` | enforced client-side: emission halts at the first stop sequence and the trailing stop text is trimmed |

`generate` is implemented on top of `generateStream`, so blocking and streaming
calls share identical maxTokens/stop semantics. Access is serialized with a
`Mutex` (the native engine/session are single-use), and all work runs on
`Dispatchers.Default`. The engine is created lazily on first use.

| Piece | File |
|-------|------|
| Contract (owned by `feat/step3-shared`) | `shared/.../llm/OnDeviceLlm.kt` |
| Implementation (`generate` + stream) | `androidApp/.../llm/AndroidOnDeviceLlm.kt` |
| Provisioning + factory | `androidApp/.../llm/LlmModelProvisioning.kt` |
| Wiring | `AppContainer.llm` / `AppContainer.isLlmModelInstalled` |
| On-device test (self-skips if absent) | `androidApp/src/androidTest/.../llm/AndroidOnDeviceLlmTest.kt` |

### Provisioning the model (kept OUT of git)

The `.task` weights (~0.5–2 GB) are **never** committed (`.gitignore` excludes
`*.task` / `*.litertlm`) and are **not** packaged in the APK — they are pushed to
the device and loaded from a real file path at runtime. The models are gated
(Gemma / Llama require accepting a license on Hugging Face / Kaggle), so there is
no unauthenticated direct download. Obtain the `.task` bundle yourself, then push
it with the helper task:

```bash
./gradlew :androidApp:pushLlmModel -PllmModel=/abs/path/gemma3-1b-it-int4.task
```

This `adb push`es the file to the app's external-files dir
(`/sdcard/Android/data/com.personalagent.android/files/models/llm/`). During
development you can also drop it at `/data/local/tmp/llm/` (the MediaPipe sample
convention) — `LlmModelProvisioning` checks both. Clones / installs **without**
the model still build and run: `LlmModelProvisioning.isModelInstalled()` /
`OnDeviceLlm.isAvailable` report `false` and the app gates the feature off.
Actual inference requires a real device with enough RAM and the model present.

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
