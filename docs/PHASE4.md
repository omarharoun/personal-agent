# Phase 4 — Reflection

**Status:** complete and tested. 🔒 shares Gate 2 (crisis) sensitivity — reflection
is emotional-wellbeing-adjacent, so its copy/cadence is part of what a human
should review before real users.

## What works

- **Optional periodic reflection** (`ReflectionScreen` + `ReflectionViewModel`):
  the user picks a cadence — **Off / Weekly / Monthly** — in one tap. Off cancels
  everything.
- **Personalized via memory:** "Reflect now" asks Hermes for a warm, low-pressure
  reflection grounded ONLY in what it remembers about the user
  (`LifePrompts.reflection`) — "like a friend checking in, not a task."
- **Never nags:**
  - Enabling a cadence anchors "now", so the first reflection is a full interval
    out (no immediate ping).
  - A daily `ReflectionWorker` (WorkManager) checks `ReflectionState.isDue` and
    posts at most one calm, `IMPORTANCE_DEFAULT` notification per interval on its
    own "Reflections" channel (independently silenceable). Showing it re-anchors
    the interval.
  - **Opting out is one tap** ("Turn off"); snooze ("Snooze a week") pushes the
    next one out.
- **No content stored on-device:** only the cadence + two timestamps
  (`ReflectionStore`, sealed at rest). Reflection text is fetched live from Hermes
  when the user opens the screen; nothing is pre-fetched or persisted.

## How it was tested

- **Hermetic:** `ReflectionTest` locks the pure due-logic — off is never due,
  weekly becomes due exactly one interval after the anchor, snooze suppresses,
  enabling doesn't nag immediately, and `markShown` re-anchors.
- The reflection prompt is covered by `LifePromptsTest` (memory-grounded,
  low-pressure, "friend checking in").
- The reflection text path reuses `HermesClient.complete`, already live-verified
  in Phases 1–3. Android compiles; debug APK builds.

## Notes

- Delivery is local (WorkManager daily check), consistent with the "app is the
  notifier, no server we control" model. Timing is day-granular, which is right
  for a weekly/monthly gentle check-in.

## 🔒 REVIEW REQUIRED (running list — unchanged count)
1. Credential + session-key storage (Phase 1).
2. Crisis handling (Phase 3) — and, by extension, the wellbeing tone of the
   reflection copy here should be reviewed alongside it.
3. Trust boundary (Phase 1).
