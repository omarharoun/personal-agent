# Phase 3 — Life-improvement layer + crisis handling 🔒

**Status:** complete and tested against live Hermes v0.18.0.

## What works

### Goals + personalized nudges (interaction design over Hermes)
- **Goals screen** (`GoalsScreen` + `GoalsViewModel`): the user defines what
  "better" means (category chips — Health, Relationships, Learning, Habits, Work
  — + free text). Each goal is stored in **Hermes memory** via a framed prompt;
  the app keeps no local copy of goal content.
- **Personalized nudge:** a button asks the agent for one short encouragement
  **grounded only in its real memory of the user**, explicitly forbidding generic
  advice (see `LifePrompts.personalizedNudge`). "Your goals" lists what the agent
  actually remembers (or says plainly it has none — no invention).
- This is **prompt/interaction design on top of Hermes, not a second AI** (the
  global rule). All wording lives in the shared, unit-tested `LifePrompts`.

**Live proof it's grounded (not generic):**
```
save goal → "Got it, locked in … training for a 5k by September …"
nudge (new conversation, same session-key) →
  "You've got a 5k in September you're training for — that's a real, dated goal …
   consistent short runs now will absolutely get you there. Lace up for one this week."
```
The nudge referenced the user's *actual* stored goal across a separate
conversation — exactly the "genuinely personalized nudge that references their
real history" the phase requires.

### 🔒 Crisis handling (Gate 2 — REVIEW REQUIRED, built, not shippable)
Wired the existing conservative crisis spine into the chat path:
- On each user turn, `ConversationViewModel` consults the coarse, conservative
  `KeywordCrisisRecognizer`. A `POSSIBLE_DISTRESS` hit surfaces a consent-first
  `SupportResponseCard` inline (warm message that **encourages reaching out to a
  real person**, real/region-aware **resources**, and — only on an explicit tap —
  help opening the dialer/SMS to a **pre-consented** trusted contact). The agent
  still replies normally; the card is dismissable.
- **The app contacts NO ONE automatically.** The autonomous-contact capability
  remains a disabled, review-gated seam (`AutonomousCrisisAction`). Every crisis
  code path is marked `🔒 REVIEW REQUIRED` / `CRISIS-CRITICAL`.
- **This is NOT shippable** until a crisis-response expert reviews it: the
  recognizer misses most genuine distress and can false-alarm; the resource list
  is placeholder and must be verified + localized. See `docs/SECURITY_REVIEW.md`
  Gate 2.

## How it was tested

- **Live** (curl through the real API): goal storage + memory-grounded nudge
  across conversations (above).
- **Hermetic:** `LifePromptsTest` locks the prompts' intent (memory-grounding,
  no-invention, low-pressure reflection). The crisis recognizer/responder already
  have `CrisisSafetyTest`. Android compiles; debug APK builds.

## 🔒 REVIEW REQUIRED (running list)
1. Credential + session-key storage (Phase 1).
2. **Crisis handling (this phase)** — recognizer + response wired into chat;
   autonomous contact built-disabled; resources are placeholders. Crisis-expert
   review required.
3. Trust boundary (Phase 1).
