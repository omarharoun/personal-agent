# CLAUDE.md — Operating Instructions for the Coding Agent

*Put this file at the repository root. It is your standing instruction set — follow it on every turn. The file `CLAUDE-CODE-SPEC-hermes-life-agent.md` is the source of truth for **what** to build; this file governs **how** you work.*

---

## The one-paragraph brief

You are building a polished **iOS + Android app** that acts as a specialized client for a **user-owned Hermes Agent** backend, focused on helping a person be better at their life (chat with memory, notes, reminders, goals, gentle reflection). Hermes provides the brain (memory, skills, scheduling, model). You build the specialized, beautiful front door. The user runs their own Hermes — **no user data lives on any server we control.** (Full detail: the spec file.)

---

## How you work (non-negotiable)

1. **Phase 0 before code.** Do the verification in the spec's Phase 0 first: read the current Hermes docs, stand up a local Hermes, and smoke-test `/health`, `/v1/capabilities`, `/v1/models`, and `/v1/chat/completions` with a real session key. **Report your findings and wait for confirmation before writing app code.**
2. **One phase at a time.** Build the spec's phases in order (1→5). Each phase must run and be testable on its own. **Commit at the end of each phase.** Do not start the next phase until the current one works.
3. **Live API beats the spec.** If the running Hermes version behaves differently from the spec, trust the live API and docs, note the difference in the README, and adapt. Don't code against assumptions the live server contradicts.
4. **Real backend, not mocks.** Anything touching Hermes is tested against an actual running instance.
5. **Stay in scope.** Only: connect, chat (with memory), notes, reminders, goals, reflection. No social features, no telemetry on user content, no autonomous actions beyond reminders, no extra integrations. If you think something out of scope is needed, **ask first.**
6. **Don't reimplement Hermes.** Memory, skills, scheduling, model routing are Hermes' job. You orchestrate and present.
7. **Keep the README current** every phase: how to run the app, how to point it at a Hermes instance, the framework choice and why, and any deviations from the spec.

---

## STOP and ask a human — do not proceed past these alone

These are the 🔒 gates from the spec. Build them, mark them `// REVIEW REQUIRED` in code, and **do not treat them as done or ship them** until a human confirms:

- **Credential + session-key storage** — API key and `X-Hermes-Session-Key` in the platform secure store (iOS Keychain / Android Keystore), never plaintext/logs/backups. A leaked or cross-used session key cross-contaminates users' memory.
- **Crisis handling** — respond with care, encourage the user to reach out to a real person, surface real crisis resources. Any **autonomous** contacting of a friend/family member without in-the-moment consent must be **built disabled** and left for dedicated human + crisis-expert review.
- **Backend trust boundary** — the app trusts only the Hermes instance the user explicitly configures. Never add a hidden or default third-party backend.

Also stop and ask before: changing the deployment model (this is Path A / bring-your-own-Hermes only), adding any server we control, or expanding scope.

---

## Definition of done (v1)

An iOS + Android app that connects to a user-owned Hermes, holds a streaming conversation the agent remembers via a securely-stored session key, does notes and reminders end-to-end, adds a personalized life-improvement + reflection layer grounded in real memory, stores no sensitive user data itself, and has the three 🔒 areas flagged for human review.

---

## KICKOFF PROMPT — paste this as your first message to the agent

> Read `CLAUDE.md` and `CLAUDE-CODE-SPEC-hermes-life-agent.md` in full before doing anything.
>
> Then execute **Phase 0 only**: read the current Hermes Agent API docs at hermes-agent.nousresearch.com/docs, install and run a local Hermes instance with the API server enabled, and smoke-test the endpoints the app depends on (`/health`, `/v1/capabilities`, `/v1/models`, and a `/v1/chat/completions` call using a Bearer key and an `X-Hermes-Session-Key`).
>
> Report back: (a) confirmation each endpoint works, (b) exactly how `X-Hermes-Session-Key` and `X-Hermes-Session-Id` behave in the version you installed, (c) any place the live API differs from the spec, and (d) your recommended mobile framework (Kotlin Multiplatform or React Native) with a one-line reason.
>
> **Do not write any app code yet.** Stop after the Phase 0 report and wait for my go-ahead.

---

*Work in small, verifiable steps. When in doubt, prefer asking over assuming. The goal is a shipped, working Phase 1 — not a big unfinished scaffold.*
