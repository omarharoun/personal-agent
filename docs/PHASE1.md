# Phase 1 — Connect + streaming chat

**Status:** complete and tested against the live Hermes v0.18.0.
**Framework:** Kotlin Multiplatform (continuing this repo) — Ktor client + Jetpack
Compose chat UI + hardware-sealed secure storage were already here; we repointed
them at Hermes rather than rewrite.

## What works

- **Connect screen** (`ui/connect/ConnectScreen` + `ConnectViewModel`): the user
  enters their Hermes base URL + API key. We normalize the URL
  (`HermesConfig.normalizeBaseUrl` — adds scheme, strips a trailing `/v1`), test
  it with `GET /health`, and only persist on a real `status: ok`. Clear,
  actionable errors on failure (bad key → 401 message, unreachable → "is it
  running?", wrong URL → "doesn't look like a Hermes"). Plaintext-remote warning
  shown (not blocking — a LAN/VPN is a valid Path-A setup).
- **Streaming chat** (`HermesClient.streamChat` + rewired `ConversationViewModel`):
  every turn goes to `POST /v1/chat/completions` with `stream:true`; SSE
  `chat.completion.chunk` deltas grow the assistant bubble token-by-token,
  terminated by `data: [DONE]`. Multi-chat history (drawer) is preserved.
- **Memory from day one**: a stable `X-Hermes-Session-Key`
  (`lifeagent:user-<hex>`) is minted once and persisted, sealed at rest. Sent on
  every request, so the agent remembers this user across app launches and across
  separate conversations. Each chat thread also carries its own
  `X-Hermes-Session-Id` for short-term threading.
- **On-device model request path retired**: chat no longer touches the bundled
  on-device LLM / cloud-escalation stack — Hermes is the brain. (Those modules
  still compile but are off the chat path; they're removed progressively in later
  phases to slim the APK.)

## How it was tested against the REAL Hermes

The Android emulator can't boot in this environment, so the *shared client code
the app ships* is exercised end-to-end over a real Ktor engine (CIO) against the
running instance — `shared/src/jvmTest/.../HermesLiveIntegrationTest.kt` (opt-in
via `HERMES_BASE_URL` + `HERMES_API_KEY`; skips otherwise so CI stays hermetic):

```
live health: status=ok version=0.18.0
live models: [hermes-agent]
live stream reply: streaming ok
live memory recall: 4273     ← fact stored in conversation A, recalled in a
                                separate conversation B under the same session-key
```

Plus hermetic `HermesClientTest` / `HermesConfigTest` (MockEngine) lock the wire
contract (deltas→text, 401→friendly message, session headers sent, URL
normalization, session-key persistence/reuse).

Run them:
```bash
# hermetic
./gradlew :shared:jvmTest --tests 'com.personalagent.shared.hermes.HermesClientTest' \
                          --tests 'com.personalagent.shared.hermes.HermesConfigTest'
# live (Hermes must be running: `hermes gateway run`)
HERMES_BASE_URL=http://127.0.0.1:8642 HERMES_API_KEY=<API_SERVER_KEY> \
  ./gradlew :shared:jvmTest --tests '*HermesLiveIntegrationTest*'
```

## 🔒 REVIEW REQUIRED items flagged this phase

1. **Credential + session-key storage** — `AppContainer.hermesConfigStore` /
   `HermesConfigStore` / `HermesConfig`. API key + session-key are sealed by the
   hardware-backed encrypted `KeyValueStorage`; never logged, never plaintext.
   Marked `🔒 REVIEW REQUIRED` in code. **Not shippable until human review.**
3. **Trust boundary** — `ConnectScreen` / `ConnectViewModel` / `HermesConfig`.
   The user-entered base URL IS the only backend; no default/hidden server.
   Marked `🔒 REVIEW REQUIRED`. (Crisis handling — item 2 — arrives in Phase 3.)

## Deviations from the spec (live wins)

- Streaming uses the OpenAI SSE surface on `/v1/chat/completions` (the chat
  client path), not the `run_events_sse` structured stream — matches Phase 0.
- Base URL is stored as the **root** origin (e.g. `http://host:8642`); the app
  appends `/v1/...` and `/api/...` itself, so users can paste either form.
- Single-user memory semantics (decided): the UI never promises multi-user
  isolation. The session-key gives cross-conversation continuity for the one
  user of this install.
