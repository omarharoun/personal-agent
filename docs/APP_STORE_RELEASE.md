# Apple App Store release checklist — Personal Agent (iOS)

This is **release-prep, NOT publishable.** The iOS app is built with SwiftUI over
the shared KMP framework; it must be built and run on a Mac with Xcode (see
`iosApp/README.md`), and the items below must clear before any submission.

> The iOS app shares the same 🔒 not-for-real-users gates as Android
> (`docs/SECURITY_REVIEW.md`): encryption/recovery (Gate 1), cloud anonymizer
> (Gate 3), and crisis safety (Gate 2, needs a crisis-response expert). Do not
> submit until those are reviewed.

## Age rating — 18+ (enforced in-app + declared in App Store Connect)

- **In-app 18+ age gate** is the first screen on iOS (`AgeGateView` +
  `AppModel.needsAgeConfirmation`, gated in `ContentView`). Date of birth is
  checked on-device via the shared `com.personalagent.shared.age` logic and is
  not stored.
- **App Store Connect age-rating questionnaire** must be answered truthfully to
  land **17+** (or the explicit **18+** restriction if offered). **Exact answers:
  see [`docs/STORE_AGE_RATING.md`](STORE_AGE_RATING.md).**
- Keep **"Unrestricted Web Access" = No** and **"Made for Kids" = Off**.

## Other submission items

- [ ] App Store privacy "nutrition label" (App Privacy) — consistent with
      `docs/PRIVACY_POLICY.md` and `docs/PLAY_DATA_SAFETY.md` (on-device-first; the
      only off-device flow is optional, off-by-default cloud escalation).
- [ ] Hosted **privacy policy URL**.
- [ ] Export-compliance answers for encryption (the app uses standard
      AES-GCM/Keychain crypto — declare accordingly).
- [ ] **On-device model licensing/distribution** decision (the ~0.5–2 GB LLM is
      provisioned at runtime, not bundled).
- [ ] Real-device QA on Apple-silicon hardware (MLX is arm64-only): encryption +
      recovery, reminders/notifications, embeddings + LLM, and the crisis surface.
- [ ] App Review note explaining the consent-first crisis-support feature and that
      the app takes **no autonomous action** and is **not** a crisis/medical service.

## Cross-references
`docs/STORE_AGE_RATING.md` · `docs/PRIVACY_POLICY.md` · `docs/PLAY_DATA_SAFETY.md` ·
`docs/SECURITY_REVIEW.md` · `iosApp/README.md`.
