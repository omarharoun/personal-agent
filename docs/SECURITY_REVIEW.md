# Security Review — gates & status

This file tracks the **🔒 HUMAN-REVIEW-REQUIRED gates** from the build brief.
A gate must be reviewed and signed off by a human before the corresponding
feature ships to real users. Gates 1 & 2 are **NOT BUILT**; Gate 3 (Step 4
cloud-escalation anonymizer) is **BUILT but PENDING HUMAN SECURITY REVIEW**.
All remain **NOT FOR REAL USERS** until signed off.

> Step 1 scope is a thin, AI-free foundation. No real user data should be stored
> in this app yet — the persistence layer is an explicit unencrypted placeholder
> (see Gate 1).

---

## 🔒 Gate 1 — Encryption / key management (Step 5)

**Status: NOT-YET-BUILT · NOT-FOR-REAL-USERS**

- The brief requires on-device data to be **encrypted at rest**. That is Step 5.
- Step 1 ships an **unencrypted** local store behind a stable interface so the
  encrypted implementation drops in later without touching any caller.
- Swap point: `com.personalagent.shared.store.KeyValueStorage`.
  Every implementation is marked `// TODO Step 5: swap for encrypted wallet`:
  - `InMemoryKeyValueStorage` (commonMain) — tests only.
  - `AndroidKeyValueStorage` (SharedPreferences, **plaintext**).
  - `IosKeyValueStorage` (NSUserDefaults, **plaintext**).
  - `FileKeyValueStorage` (JVM, **plaintext** properties file).
- `MemoryEntry.embedding` is reserved for an on-device embedding model
  (tech-from-measurement). The model + vector store choice is **deferred**, not
  decided here.

**Human review required before Step 5 ships:** key generation & storage
(Keystore/Keychain/Secure Enclave), at-rest cipher choice, key rotation,
backup/exclusion policy, and a migration path for any data written by the Step-1
placeholder.

---

## 🔒 Gate 2 — Crisis autonomous action / placing a call (Step 7)

**Status: NOT-YET-BUILT · NOT-FOR-REAL-USERS**

- The brief's crisis feature (the agent autonomously contacting help, e.g.
  placing a call) is **Step 7** and is **not present anywhere in this codebase**.
- There is **no** dialing, emergency-contact, or autonomous-action code in
  Step 1. Do not add any until this gate is designed and human-reviewed.

**Human review required before Step 7 ships:** false-positive/false-negative
risk of crisis detection, explicit user consent model, jurisdiction/legal
constraints on autonomous calling, escalation/oversight, auditability, and
fail-safe behaviour. This gate must not be implemented by the agent unprompted.

---

## 🔒 Gate 3 — Cloud-escalation payload anonymization (Step 4)

**Status: BUILT · PENDING-HUMAN-SECURITY-REVIEW · NOT-FOR-REAL-USERS**

This is the on-device payload-prep that runs before *anything* is sent to a
remote model on cloud escalation. **A subtle mistake here silently defeats the
entire privacy guarantee** — data the user believes is protected leaves the
device. It must not ship to a real user until a human signs off.

- **Code:** `com.personalagent.shared.cloud.DefaultPayloadPrep` (commonMain),
  marked `// 🔒 SECURITY-CRITICAL`. Tests: `DefaultPayloadPrepTest` (`:shared:jvmTest`).
- **Contract:** `PayloadPrep` / `PreparedPayload` / `RehydrationMap` in
  `cloud/PayloadPrep.kt` (owned by the `feat/step4-shared` sibling; wire
  `DefaultPayloadPrep` as the production impl in place of its passthrough).

### What to review (and why)
1. **Minimization is the primary defense — confirm it stays that way.**
   The guarantee holds because we *send as little as possible*, NOT because we
   scrub. Context re-identifies a person even with every proper noun removed
   ("my manager at the Springfield plant I carpool with"), so no scrubber can
   make an over-sized payload safe. Reviewer must check that the escalation path
   minimizes *what is even asked* upstream; `DefaultPayloadPrep.minimize()` is
   only the conservative floor (whitespace + greeting/sign-off removal).
2. **Anonymization is best-effort, not a guarantee — confirm nothing overclaims.**
   PII detection is **regex + heuristic**. It misses (lowercased names, novel
   formats, indirect references) and over-matches (capitalized common nouns).
   No code path or user-facing copy may claim "all personal data is removed."
   Review the patterns, the location gazetteer, and the proper-noun stopword
   list for false-negatives that matter for this app's data.
3. **🔒 INVARIANT — the mapping NEVER leaves the device.**
   `RehydrationMap` is the key that undoes the anonymization; if it travels with
   the payload, anonymization is worthless. It is deliberately **not**
   `@Serializable`, its contents are `private` (only `internal` accessors), and
   its `toString()` is redacted. **These are guardrails, not proofs** — the
   reviewer must trace every outbound request body and confirm no
   `RehydrationMap` (or its values) is ever serialized, logged, or attached to a
   cloud request. Rehydration happens on-device in `rehydrate()` after the cloud
   replies in terms of tokens.
4. **Token round-trip correctness.** Confirm tokens are stable (same entity →
   same token), prefix-safe on rehydration (`<PERSON_1>` vs `<PERSON_11>`), and
   that no real value survives in `anonymizedText` (covered by tests, but the
   detection set is not exhaustive — see #2).

### Sign-off required before this ships
A human security reviewer must confirm: minimization-first is enforced
end-to-end; the no-off-device-mapping invariant holds across all transport code;
detection gaps are documented and acceptable for the data actually escalated;
and user-facing copy does not overclaim. Until then: **NOT-FOR-REAL-USERS.**

---

## Changelog
- **Step 1:** gates documented; both NOT-BUILT. Persistence is an explicit
  unencrypted placeholder behind `KeyValueStorage`.
- **Step 4:** Gate 3 added — cloud-escalation anonymizer (`DefaultPayloadPrep`)
  BUILT and PENDING-HUMAN-SECURITY-REVIEW. Minimize-first is the primary defense;
  tokenization is best-effort; `RehydrationMap` is on-device-only and never
  serialized off-device.
