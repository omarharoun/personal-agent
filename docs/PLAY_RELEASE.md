# Google Play release checklist — Personal Agent (Android)

This is **release-CONFIG-ready, NOT publishable.** The build is wired for a Play
release (R8 minify + resource shrinking, AAB output, signing-from-properties),
but the items under "🔒 Hard blockers" below MUST clear first. Nothing here marks
the app ready for real users.

---

## 🔒 Hard blockers — must clear before ANY public release

1. **Three human-review gates (`docs/SECURITY_REVIEW.md`) must be signed off:**
   - **Gate 1 — Encryption / key management** (BUILT, pending human security
     review; cross-device recovery escrow not yet wired).
   - **Gate 3 — Cloud-escalation anonymizer** (BUILT, pending human security
     review; best-effort, must not overclaim).
   - **Gate 2 — Crisis safety** (consent-first spine BUILT, autonomous trigger
     DISABLED) — **requires review by a crisis-response expert**, not just an
     engineer. Until then the crisis surface is NOT-FOR-REAL-USERS.
2. **Real release signing key.** Create a keystore, put its details in a
   gitignored `keystore.properties` (see `keystore.properties.example`), and
   build with it. The sandbox AAB is **debug-signed** and cannot be uploaded.
   Decide on **Play App Signing** (recommended) and keep the upload key safe.
3. **Real-device QA.** Everything in this repo is sandbox-built. Run the release
   build on physical devices across API 26→36: encryption/keystore + recovery
   flow, reminders firing (exact-alarm permission), on-device embeddings + LLM
   with a provisioned model, and the crisis surface (dialer/SMS intents).
4. **Gated on-device LLM model — licensing + distribution decision.** The ~0.5–2 GB
   model (e.g. Gemma / Llama `.task`) is **not** in the AAB. Decide and implement
   one of: in-app download-on-first-run from a license-accepted source, or a
   documented sideload. Confirm the model's license permits your distribution
   method, and surface its terms to the user. (The embedding model is also
   provisioned, not bundled.)
5. **Crisis / mental-health content may trigger Play sensitive-content review.**
   Be ready for extra scrutiny; provide accurate, localized crisis resources
   (the shipped ones are clearly-marked placeholders) and do not present the app
   as a crisis service.

---

## Age restriction — 18+ (enforced in-app + declared in Play)

- [x] **In-app 18+ age gate** is the first onboarding step on both platforms
      (date-of-birth confirmation, checked on-device via the shared, unit-tested
      `com.personalagent.shared.age` logic; under-18 users are blocked and cannot
      proceed; DOB is not stored). Android: `AgeGateScreen` + `AgeGateRepository`,
      gated in `MainActivity`. iOS: `AgeGateView` + `AppModel`, gated in `ContentView`.
- [ ] **Play "App content" → Target audience: select only the 18+ age band.**
- [ ] **Do NOT opt into Google Play Families / "Designed for Families."**
- [ ] **IARC content-rating questionnaire:** answer for a **mature (18+)** audience
      and disclose the crisis / mental-health support content.
- [x] **Manifest carries no children's-app flag** (no `Designed for Families`
      meta-data, no kids-category opt-in) — verified in `AndroidManifest.xml`.

## Store listing & policy

- [ ] **Privacy policy** hosted at a public URL (draft: `docs/PRIVACY_POLICY.md`) —
      includes the 18+ eligibility clause.
- [ ] **Data Safety form** completed truthfully (draft: `docs/PLAY_DATA_SAFETY.md`) —
      target audience 18+ only.
- [ ] **Content rating** questionnaire (IARC) — 18+/mature; answer honestly re: the
      mental-health/crisis content. **Exact answers: see
      [`docs/STORE_AGE_RATING.md`](STORE_AGE_RATING.md).**
- [ ] App title, short + full description, screenshots, feature graphic, app icon.
- [x] Target audience = **18+ only**; **not** "designed for families".

## Permissions justification (Play asks for these)

| Permission | Why | Notes |
|------------|-----|-------|
| `POST_NOTIFICATIONS` | Show reminder notifications | Runtime-requested (API 33+) |
| `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` | Reminders fire at the exact minute | Exact-alarm policy: justify use of an alarm clock/reminder; consider the user-facing "Alarms & reminders" special access on API 31+ |
| `RECEIVE_BOOT_COMPLETED` | Re-arm scheduled reminders after reboot | |
| (No `CALL_PHONE`) | Crisis contact uses `ACTION_DIAL`/`SENDTO` only — never auto-calls | Intentional: keep it out |
| Internet (implicit) | Optional cloud escalation (off by default) + optional model download | |

## Technical / build

- [x] `targetSdk` / `compileSdk` = **36** (meets Play's current target-API floor).
- [x] **R8 minify + resource shrinking** enabled on `release`; keep-rules in
      `androidApp/proguard-rules.pro` (ONNX, MediaPipe, Ktor/OkHttp,
      kotlinx-serialization, JNI). Re-verify keep-rules after dependency bumps.
- [x] Release artifact is an **AAB** (`:androidApp:bundleRelease`).
- [x] `versionCode = 1`, `versionName = "1.0.0"`.
- [ ] Build the **release-signed** AAB (supply `keystore.properties`).
- [ ] Pre-launch report / closed testing track before production.
- [ ] Confirm no model weights or secrets in the AAB (current sandbox AAB: none;
      see the build report below / `docs/PLAY_RELEASE.md` regen step).
- [ ] R8 release **smoke test on a device** — minification can break reflective
      paths that unit tests don't exercise.

## What is verified in this repo vs. what still needs your machine

- ✅ **Verified in sandbox:** the release AAB **builds with R8 minification +
  resource shrinking** (debug-signed fallback); shared logic is unit-tested
  (`:shared:jvmTest`).
- ⚠️ **Needs your environment:** release signing key, physical-device QA, the
  model provisioning/licensing decision, and the three human reviews above.
