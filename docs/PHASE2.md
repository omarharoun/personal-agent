# Phase 2 — Notes & reminders

**Status:** complete and tested against the live Hermes v0.18.0.

## What works

### Reminders — via Hermes `/api/jobs`, delivered by local polling
- **Two creation paths, one source of truth (Hermes):**
  - **Conversational** — "remind me to call my sister in 90 minutes" in chat →
    Hermes creates the cron job itself (verified live: it created a job named
    "Call sister", `once in 90m`). Hermes is the brain.
  - **Explicit** — the Reminders screen (`RemindersScreen` + `RemindersViewModel`)
    creates a one-shot job via `POST /api/jobs` (`schedule` = a duration like
    `"90m"` from `oneShotScheduleMinutes`), lists live jobs, and cancels them.
- **Delivery = the app polls, no server we control, no push service** (the chosen
  model). `ReminderPollWorker` (WorkManager) calls `HermesReminderPoller.pollOnce`:
  `GET /api/jobs` → `ReminderPolling.dueNow(...)` picks jobs whose `next_run_at`
  has passed → raises a **local** notification (`ReminderNotifier`) → records the
  opaque fire-key so each firing notifies once. Scheduled as a 15-min periodic
  safety-net poll plus a punctual one-time poll at each reminder's due time.
- **No second copy of content:** the reminder *text* is read from Hermes at poll
  time; the app persists only `job-id@run-time` fire-keys (`NotifiedReminderStore`).

### Notes — captured conversationally into Hermes memory
- Chat already stores anything you tell it ("Remember that…").
- The Notes screen (`NotesScreen` + `NotesViewModel`) is a quick-capture box that
  sends the note to Hermes to store in **its** memory and shows the agent's
  confirmation. The app keeps **no** persistent copy of note content (only an
  ephemeral this-session list for UX feedback).

## How it was tested against the REAL Hermes

Live `/api/jobs` and the full poll→notify path, driven through the shipped shared
code over a real Ktor CIO engine (`HermesLiveIntegrationTest`):

```
live job created: id=e46cafad78ef next=2026-07-05T00:20:26+03:00 display=once in 90m
live job deleted ok
live poller notified: itest-poll-1783194626269   ← poller read the real job,
                                                    decided it was due, notified
                                                    ONCE (2nd poll suppressed)
```
Also verified by curl: a chat message "set a reminder to call my sister in 90
minutes" caused Hermes to create the job server-side. Hermetic `HermesJobsTest`
locks the wire shape + the `dueNow` selection logic (active/recent/unnotified).

Run:
```bash
HERMES_BASE_URL=http://127.0.0.1:8642 HERMES_API_KEY=<key> \
  ./gradlew :shared:jvmTest --tests '*HermesJobsTest' --tests '*HermesLiveIntegrationTest'
```

## Deviations / notes

- **`/api/jobs`, not `/v1/cron`** (Phase 0 finding). One-shot reminders use a
  duration `schedule` (`"90m"`) so the app never has to reason about the server's
  timezone; the app compares the returned `next_run_at` to the device clock.
- **Polling latency:** WorkManager's periodic floor is 15 min and delayed one-shot
  work isn't exact under Doze, so a background reminder can be a few minutes late.
  That is the honest trade of the "poll + local notification" model (no push). The
  Reminders screen always shows the true live state from Hermes.

## 🔒 REVIEW REQUIRED

No new 🔒 gates this phase. Existing ones stand (credentials/session-key storage,
trust boundary from Phase 1). Crisis handling (gate 2) arrives in Phase 3.
