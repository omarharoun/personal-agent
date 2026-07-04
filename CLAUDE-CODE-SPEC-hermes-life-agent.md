# Claude Code Project Spec — Life Agent (Hermes client)

*Instructions for Claude Code (Anthropic's coding agent). Read this whole file before writing any code. Follow the phases in order. Do not skip Phase 0. Verify every Hermes API detail against the live docs — Hermes moves fast (already past v0.14) and specifics change.*

---

## What you're building

A polished, consumer-friendly **iOS + Android app** that is a *client* for a **Hermes Agent** backend (Nous Research's self-hosted, self-improving agent) and specializes it for one job: **helping a person be better at their life** — remembering, notes, reminders, planning, and gentle reflection.

You are **not** building the agent, the memory system, or the model. Hermes already provides those. You are building the *specialized front door* to them.

### The deployment model (decided — do not change)

**Path A — bring-your-own-Hermes.** The user runs their own Hermes instance (local machine or a cheap VPS) and the app connects to it. This keeps all memory and data on the user's own server — the app stores no sensitive user data itself. Do **not** build multi-tenant hosting, user accounts on our servers, or anything that puts user data on infrastructure we control. The app is a thin, trusted client to a server the *user* owns.

> If a future "managed hosting" path is ever requested, treat it as a separate project with its own privacy and security review. It is out of scope here.

---

## Phase 0 — Verify reality before building (MANDATORY)

Do these first. If any assumption below is wrong in the current Hermes version, stop and report before proceeding.

1. **Read the current Hermes API docs.** Start at `https://hermes-agent.nousresearch.com/docs` — specifically the "API Server" and "Integrations" pages. Confirm the endpoints, headers, and auth described below still match.
2. **Stand up a local Hermes instance** to develop against (`pip install hermes-agent`, then `hermes setup`; enable the API server via `API_SERVER_ENABLED=true` and `API_SERVER_KEY=...`). Point it at any working LLM provider.
3. **Smoke-test the surface** the app depends on:
   - `GET /health` → expect `{"status":"ok"}`
   - `GET /v1/capabilities` → confirm `chat_completions`, streaming (`run_events_sse`), and `session_key_header` support
   - `GET /v1/models` → confirm the agent is listed
   - `POST /v1/chat/completions` with a `Bearer` key and a test message → confirm a response
4. **Confirm memory scoping.** Verify that passing `X-Hermes-Session-Key` (a stable per-user string, e.g. `lifeagent:user-<id>`) scopes memory correctly, and that `X-Hermes-Session-Id` is the transcript-scoped id that rotates on new conversations. Report exactly how the running version behaves.

Only after Phase 0 passes do you write app code.

---

## Known integration surface (verify in Phase 0)

- **Transport:** OpenAI-compatible HTTP. Default base URL `http://<host>:8642/v1`.
- **Auth:** `Authorization: Bearer <API_SERVER_KEY>`.
- **Core call:** `POST /v1/chat/completions` (SSE streaming supported).
- **Per-user memory scope:** `X-Hermes-Session-Key: lifeagent:user-<stableId>` (max 256 chars, no control chars). This is what makes the agent remember *this* user across sessions. Get it right — it is the whole point.
- **Transcript id:** `X-Hermes-Session-Id` for a given conversation thread.
- **Discovery:** `GET /v1/models`, `GET /v1/capabilities`, `GET /health`.
- **Important constraints to design around:**
  - The `model` field in requests is **cosmetic** — the real model is set server-side in the user's Hermes `config.yaml`. Don't build model-picking in the app for v1.
  - **No file upload** through the API (inline images only). Don't build attachment upload for v1.
  - **Tools run server-side**, on the Hermes host, not on the phone. The app never executes agent tools locally.

---

## Build phases

Each phase must be independently runnable and testable before you start the next. Commit at the end of each.

### Phase 1 — Connect + chat
- App scaffold for iOS + Android. Use **Kotlin Multiplatform** or **React Native** (pick one; state the choice and why in the README).
- A "Connect" screen: user enters their Hermes base URL + API key. Test the connection with `GET /health` and surface a clear success/failure.
- A clean chat screen: send a message via `/v1/chat/completions`, stream the reply, render it well.
- Generate and persist (on-device, securely) a stable `X-Hermes-Session-Key` for this user so memory works from day one.
- **Done when:** the user can connect to their own Hermes and have a streaming conversation that the agent remembers next time.

### Phase 2 — Notes & reminders (the practical core)
- Let the user capture notes conversationally; the agent stores them in *its* memory (server-side) — the app doesn't keep a second copy of sensitive content.
- Reminders: let the user set them; use Hermes' built-in cron/scheduling (verify the current REST cron surface in Phase 0) so reminders fire from the agent side and can reach the user.
- **Done when:** a user can say "remind me to call my sister Sunday" and it works end-to-end.

### Phase 3 — Life-improvement layer  🔒 (see Safety)
- A lightweight goals view: the user defines what "better" means to them (health, relationships, learning, habits).
- The app frames prompts to the agent that pull on its accumulated memory to surface patterns and encouragement — grounded in the user's *actual* history, never generic.
- Keep this as *prompt/interaction design on top of Hermes*, not a second AI. Hermes is the brain.
- **Done when:** the agent gives the user a genuinely personalized nudge that references their real history.

### Phase 4 — Reflection  🔒 (see Safety)
- Optional periodic reflection prompts (weekly/monthly), always easy to snooze or turn off.
- Personalized via memory; never nagging.
- **Done when:** reflections feel like a friend checking in, and opting out is one tap.

### Phase 5 — Polish
- Onboarding that walks a non-technical-ish user through pointing the app at their Hermes (this is the hardest UX problem in Path A — make it as painless as possible; consider a clear setup guide/link).
- Visual polish, empty states, error handling, accessibility.

---

## Global engineering rules

- **Don't reimplement Hermes.** Memory, skills, scheduling, model routing = Hermes. The app orchestrates and presents.
- **The app stores no sensitive user content.** Conversations and memory live in the user's Hermes. The app holds only: connection config (URL, key) and the session-key — both secured on-device (see Safety).
- **Stay in scope.** Only: connect, chat, notes, reminders, goals, reflection. No social features, no analytics/telemetry on user content, no autonomous actions beyond reminders.
- **Fail clearly.** Network/auth errors get honest, actionable messages (the user is talking to *their own* server — help them fix it).
- **Test against a real running Hermes**, not mocks, for anything touching the API.

---

## 🔒 Safety-critical — build, then flag for human review

Write these, mark them clearly in code as `// REVIEW REQUIRED`, and do not treat them as shippable until a human has reviewed them.

**1. Credential + session-key storage (Phases 1–2).**
Store the Hermes API key and the session-key in the platform secure store (iOS Keychain / Android Keystore), never in plaintext, logs, or app backups. The session-key is a memory-access scope — leaking it or reusing another user's scope would cross-contaminate memory. Review for exactly this.

**2. Crisis handling (Phases 3–4).**
If the user expresses genuine distress, the app/agent must: respond with care, **encourage the user to reach out to a real person themselves**, and surface real crisis resources. Any feature that would **autonomously contact** a friend/family member without the user's clear, in-the-moment consent must be **built disabled** and left for dedicated human + crisis-expert review. The hard part is judging *when* — never ship that trigger as a simple rule.

**3. Trust boundary.**
Because tools run server-side on the Hermes host, the app is trusting the user's own server — that's fine for Path A. But never add a feature that would let a *third party's* server act as the backend without the user explicitly configuring it. No hidden default backends.

---

## How to work (for the coding agent)

- Do Phase 0 fully; report findings before building.
- Build one phase at a time; keep each runnable; commit between phases.
- When the live Hermes API differs from this spec, **trust the live API and docs**, note the difference, and adapt.
- Flag the 🔒 items in code as you reach them.
- Keep a short `README.md` current: how to run the app, how to point it at a Hermes instance, and any deviations from this spec.

## Definition of done (v1)
An iOS + Android app that connects to a user-owned Hermes instance, holds a streaming conversation the agent remembers via a securely-stored session-key, supports notes and reminders end-to-end, adds a personalized life-improvement + reflection layer grounded in real memory, stores no sensitive user data itself, and has the three 🔒 areas reviewed before any real user relies on them.
