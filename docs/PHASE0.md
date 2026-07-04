# Phase 0 — Verify reality before building (Hermes Life Agent client)

**Status:** Complete. **Do not write app code until the human confirms.**
**Verified against:** a live, locally-installed **Hermes Agent v0.18.0 (2026.7.1)** on this host
(`~/.hermes/hermes-agent`, config `~/.hermes/config.yaml`), Python 3.11.15, OpenAI SDK 2.24.0.
**Date:** 2026-07-04.

The rule from the spec applies: **when the live API differs from the spec, trust the live API.** Deviations are called out below.

---

## 0. How the API server was enabled (v0.18.0)

The OpenAI-compatible API server is **not** a top-level `hermes` subcommand. It is a **platform adapter of the messaging gateway** (`gateway/platforms/api_server.py`). To turn it on:

1. In `~/.hermes/.env` (outside this repo — never committed):
   - `API_SERVER_ENABLED=true`
   - `API_SERVER_KEY=<random secret>`  (Bearer token clients must present)
   - `API_SERVER_HOST=127.0.0.1`, `API_SERVER_PORT=8642` (both are the defaults)
2. Start it with **`hermes gateway run`** (foreground) — the API server starts alongside any messaging platforms. `hermes gateway stop` / `pkill -f "hermes gateway run"` stops it.

> Note: `hermes serve` is a *different* server — the JSON-RPC/WebSocket gateway for the desktop app (default port **9119**). It is **not** the OpenAI-compatible `/v1` surface. Don't point the mobile app at `serve`.

**LLM provider used for the smoke test:** the config default is `provider: anthropic`, `model: claude-opus-4-8`. `ANTHROPIC_API_KEY` was empty (only an OAuth `ANTHROPIC_TOKEN` was present), so the user's Anthropic API key was placed in `~/.hermes/.env` (`ANTHROPIC_API_KEY=...`) **for the test only** — it lives outside the repo, was never printed, and is not committed. Chat succeeded against real Claude, confirming the full path works.

---

## (a) Endpoint-by-endpoint results (observed live)

All calls to `http://127.0.0.1:8642`. Auth = `Authorization: Bearer <API_SERVER_KEY>`.

| Endpoint | Spec expectation | Live result (v0.18.0) | Verdict |
|---|---|---|---|
| `GET /health` | `{"status":"ok"}` | `{"status":"ok","platform":"hermes-agent","version":"0.18.0"}` — **no auth required** | ✅ Works (richer body) |
| `GET /v1/capabilities` | `chat_completions`, streaming, session-key support | Returns all three: `chat_completions:true`, `chat_completions_streaming:true`, `run_events_sse:true`, `session_key_header:"X-Hermes-Session-Key"`, `session_continuity_header:"X-Hermes-Session-Id"`. **Requires Bearer** (401 without). | ✅ Works |
| `GET /v1/models` | agent listed | `{"object":"list","data":[{"id":"hermes-agent",...}]}` | ✅ Works |
| `POST /v1/chat/completions` (non-stream) | real response | `200`, standard OpenAI `chat.completion` shape; model echoed as `"hermes-agent"`; real Claude answer; `usage` populated (prompt ~23.7k tokens — the agent's system/memory context). | ✅ Works |
| `POST /v1/chat/completions` (`stream:true`) | SSE | Standard OpenAI `chat.completion.chunk` deltas, terminated by `data: [DONE]`. First chunk carries `delta.role`, subsequent carry `delta.content`, final has `finish_reason:"stop"` + `usage`. | ✅ Works |

**Auth behavior:** `GET /health` is open; **all `/v1/*` and `/api/*` require the Bearer key** (missing/wrong → `401`). Sending `X-Hermes-Session-Key` **without** a configured `API_SERVER_KEY` is rejected `403` (by design — an unauthenticated caller must not be able to inject itself into a memory scope).

**Full route list registered by the v0.18.0 API server** (from `gateway/platforms/api_server.py`):

```
GET  /health                         GET  /health/detailed        GET /v1/health
GET  /v1/models                      GET  /v1/capabilities
POST /v1/chat/completions            POST /v1/responses           GET/DELETE /v1/responses/{id}
POST /v1/runs                        GET  /v1/runs/{id}           GET /v1/runs/{id}/events (SSE)
POST /v1/runs/{id}/approval          POST /v1/runs/{id}/stop
GET  /v1/skills                      GET  /v1/toolsets
GET/POST /api/sessions               GET/PATCH/DELETE /api/sessions/{id}
GET  /api/sessions/{id}/messages     POST /api/sessions/{id}/fork
POST /api/sessions/{id}/chat[/stream]
GET/POST /api/jobs                   GET/PATCH/DELETE /api/jobs/{id}
POST /api/jobs/{id}/pause|resume|run     POST /api/cron/fire
```

---

## (b) How `X-Hermes-Session-Key` and `X-Hermes-Session-Id` behave in v0.18.0

Both are **optional** request headers; a caller may send either, both, or neither. Both are echoed back in the response headers. Max length **256 chars**; control chars (`\r \n \0`) rejected `400`; either requires Bearer auth (else `403`).

- **`X-Hermes-Session-Id`** = *transcript / conversation-thread* id. If omitted, the server **auto-generates one** (observed: `api-a8d1b7f87b94f18f`) and returns it in the response headers. Reusing the same id continues that thread's short-term context; a new/absent id starts a fresh transcript. This matches the spec ("transcript-scoped id that rotates on new conversations") — **the app is responsible for holding one id per open conversation and starting a new id per new chat.**
- **`X-Hermes-Session-Key`** = *long-term memory scope* — a stable per-user string (e.g. `lifeagent:user-<id>`). It is validated and echoed. Independent of the session-id.

### ⚠️ MAJOR FINDING — session-key does **not** isolate memory under the default (built-in-only) config

Empirical test (3 turns):
1. `key=lifeagent:user-alice`, fresh transcript → "remember: favorite color teal, dog Pixel" → agent acknowledged and **wrote it to memory**.
2. `key=lifeagent:user-alice`, **new** transcript → "what's my color / dog?" → **correctly recalled** "teal / Pixel". ✅ cross-transcript recall works.
3. `key=lifeagent:user-bob-unrelated` (**different** key), new transcript → same question → **also answered "teal / Pixel"**. ❌ **cross-user leak.**

**Root cause (confirmed):** `hermes memory status` shows `Provider: (none — built-in only)`. The built-in memory ("always active") writes to a **single global** `~/.hermes/memories/USER.md` / `MEMORY.md` that is **shared across every session-key**. The fact landed in that one global file (verified: `USER.md` contained `Favorite color is teal. Has a dog named Pixel.`). The `X-Hermes-Session-Key` header is honored for **routing to an external per-scope memory provider** (Honcho, mem0, hindsight, openviking, holographic, retaindb, byterover, supermemory — all installed but **none active**). With only built-in memory, the key is accepted and echoed but produces **no per-user isolation.**

**Implications for the app (must decide before Phase 1 "done"):**
- The spec's premise — "`X-Hermes-Session-Key` is what makes the agent remember *this* user across sessions… get it right, it is the whole point" — **only holds when the user's Hermes has an external, per-scope memory provider enabled.** On a stock single-user Hermes it does not.
- This is exactly the 🔒 *credential + session-key* safety concern, but deeper: even a *correctly unique* key gives no isolation without an external provider. For **Path A single-user** (one human runs their own Hermes for themselves) this is acceptable — there is only one user. But onboarding must **not promise multi-user memory isolation** on a default install, and any shared-Hermes scenario is unsafe until an external provider is configured.
- **Recommendation:** treat "external memory provider configured" as a documented prerequisite for the memory-scoping guarantee, surface memory-provider status via `/v1/capabilities`/`memory` in onboarding, and keep generating a stable unique key regardless (it's necessary but not sufficient). Flag for the human review of 🔒 item #1.

*(Test artifact cleanup: the injected `USER.md` line was cleared after testing so the user's memory is left clean.)*

---

## (c) Every place the live v0.18.0 API differs from the spec

| Spec said | Live v0.18.0 | Action |
|---|---|---|
| Enable via `pip install hermes-agent` + `hermes setup`, env `API_SERVER_ENABLED`/`API_SERVER_KEY` | Already installed v0.18.0. API server is a **gateway platform**, started with **`hermes gateway run`** (not a standalone server cmd). Env vars are correct. | Document the `gateway run` start path. |
| Base URL `http://<host>:8642/v1`, Bearer auth | ✅ Exactly — default `127.0.0.1:8642`, Bearer. | None. |
| `/health`, `/v1/capabilities`, `/v1/models`, `/v1/chat/completions` | ✅ All present and working. | None. |
| Streaming named "`run_events_sse`" | Two streaming surfaces: **(1)** OpenAI SSE on `/v1/chat/completions` with `stream:true` (this is what a chat client uses) and **(2)** the structured `run_events_sse` on `/v1/runs/{id}/events`. `run_events_sse:true` in capabilities refers to (2). | Use `/v1/chat/completions` `stream:true` for the chat UI. |
| `X-Hermes-Session-Key` scopes per-user memory | Header works, but **isolation requires an external memory provider** (see (b)). Default build = one shared memory. | See (b) — big deviation. |
| "verify the current **REST cron surface** for reminders" (Phase 2) | Reminders/cron REST surface exists but as **`/api/jobs`** (CRUD + `pause`/`resume`/`run`) and `POST /api/cron/fire` — **not** `/v1/cron`. **Not advertised** in `/v1/capabilities.endpoints` and gated by `_CRON_AVAILABLE`; capabilities reports `jobs_admin:false`. `GET /api/jobs` returned `200 {"jobs":[]}`. | Phase 2 uses `/api/jobs`. Verify delivery path (below). |
| `model` field is cosmetic | ✅ Confirmed — response echoes `"hermes-agent"`; real model is server-side config (`claude-opus-4-8`). Don't build a model picker. | None. |
| No file upload; inline images only | Consistent — multimodal image parts handled in `_normalize`, no upload route. | Don't build attachment upload. |
| Tools run server-side | ✅ `capabilities.runtime.tool_execution:"server"`, `split_runtime:false`. | App never runs tools locally. |

**Reminder delivery caveat (Phase 2):** cron jobs fire *on the Hermes host*. For a reminder to actually **reach the user**, Hermes needs a delivery channel (a messaging platform via the gateway, push, etc.). The API server itself is request/response — it does not push to the phone. Phase 2 must define how a fired reminder reaches the app (poll `/api/jobs`, a configured messaging platform, or push). This needs a decision before Phase 2.

---

## (d) Recommended mobile framework — **Kotlin Multiplatform (continue this repo)**

**One-line reason:** this repo is *already* a working KMP app (Ktor HTTP client, Compose Multiplatform chat UI, secure storage, notes/reminders), so continuing in KMP reuses ~all of the plumbing the Hermes client needs and avoids a rewrite.

How the existing app maps onto the Hermes-client architecture:

| Existing piece | Reuse / change for Hermes client |
|---|---|
| Ktor client + chat networking | **Reuse.** Repoint at `<baseURL>/v1/chat/completions`; add `Authorization: Bearer`, `X-Hermes-Session-Key`, `X-Hermes-Session-Id` headers; parse OpenAI `chat.completion` + SSE `chat.completion.chunk`/`[DONE]`. |
| Compose chat UI + streaming render | **Reuse.** Feed it SSE deltas instead of the current on-device model output. |
| Secure storage (Keychain/Keystore) | **Reuse + harden** for 🔒 #1 — store base URL, API key, and the generated session-key here. |
| On-device model / embedder / memory graph (recent commits) | **Retire from the request path.** Hermes is the brain now (memory, skills, scheduling, model). Don't reimplement — per Global Rule "Don't reimplement Hermes." Keep code around but off the Hermes path. |
| Notes / reminders (local) | **Change.** Notes → agent memory (server-side); reminders → Hermes `/api/jobs`. App stops keeping a second copy of sensitive content. |
| Connect/settings screen | **Add/adapt** a "Connect to your Hermes" screen: base URL + API key, tested with `GET /health`. |

*(React Native would be a from-scratch rewrite for zero benefit here.)*

---

## (e) Secret hygiene — confirmed

- The Anthropic API key and the generated `API_SERVER_KEY` live **only** in `~/.hermes/.env` (outside this git repo), were **never printed**, and are **not** committed.
- Repo scan for `sk-ant-api03`, the key body, and `API_SERVER_KEY=` → **no matches** in the tree.
- `git ls-files` tracks no `.env`/`.hermes` files. This commit adds only the two spec docs + this note.

---

## STOP — awaiting human go-ahead

Phase 0 is complete and reported. **No app code will be written until confirmed.** Open decisions the human should weigh in on before Phase 1/2:
1. **Memory isolation** — accept single-user Path A semantics (built-in memory, one user) *or* require an external memory provider for the per-user guarantee. (Ties to 🔒 #1.)
2. **Reminder delivery path** (Phase 2) — how a fired `/api/jobs` reminder reaches the app.
