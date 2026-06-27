# Security Review — gates & status

This file tracks the **🔒 HUMAN-REVIEW-REQUIRED gates** from the build brief.
A gate must be reviewed and signed off by a human before the corresponding
feature ships to real users. Gate 2 is **NOT BUILT**; Gate 1 (Step 5 encryption
+ recovery) and Gate 3 (Step 4 cloud-escalation anonymizer) are **BUILT but
PENDING HUMAN SECURITY REVIEW**. All remain **NOT FOR REAL USERS** until signed off.

> Step 1 scope is a thin, AI-free foundation. No real user data should be stored
> in this app yet — the persistence layer is an explicit unencrypted placeholder
> (see Gate 1).

---

## 🔒 Gate 1 — Encryption / key management (Step 5)

**Status: BUILT · PENDING-HUMAN-SECURITY-REVIEW · NOT-FOR-REAL-USERS**

The brief requires on-device data to be **encrypted at rest**, recoverable ONLY
with the device hardware key OR a user-held recovery code — **never** by the
company. Step 5's shared crypto core is now BUILT (the testable common layer +
vetted JVM crypto). It must not ship to a real user until a human signs off.

### What was built (this slice — `feat/step5-shared`)

All files are marked `// 🔒 SECURITY-CRITICAL (Step 5)` and use **vetted standard
crypto** (AES-256-GCM, PBKDF2-HMAC-SHA256), never hand-rolled primitives.

- **`crypto.SecretKeyProvider`** — the hardware-backed AEAD contract. The platform
  Keystore / Secure Enclave holds the key; it never leaves hardware. `encrypt`/
  `decrypt` are AES-GCM with a 12-byte nonce prepended. **Platform impls (Android
  Keystore / iOS Secure Enclave) are owned by sibling slices and are the part that
  is only verifiable on-device.**
- **`crypto.EncryptedKeyValueStorage`** — the **Step-5 swap** at the existing
  `store.KeyValueStorage` seam (the `// TODO Step 5` placeholder). Wraps any
  plaintext delegate, encrypting values on write / decrypting on read, so the
  whole local wallet (memory/notes/reminders/…) is encrypted at rest. The logical
  key is bound as **AAD** so ciphertext can't be moved between keys. Optional
  `storageKeyTransform` can make delegate keys opaque (hashed) too.
- **`crypto.RecoveryManager` + `WrappedDataKey`** — **dual-wrap envelope**. A random
  256-bit DEK encrypts the wallet; the DEK is wrapped under BOTH (1) the hardware
  key and (2) a key derived (PBKDF2) from a **high-entropy user-held recovery
  code** (`crypto.RecoveryCode`, 130-bit, Base32). Only the two wraps + public salt
  + KDF params are persisted. **🔒 No company-side key path:** the company never
  sees/stores/can-regenerate the hardware key or the recovery code, so it cannot
  decrypt or reset access; losing both device key AND code = unrecoverable by design.
- **Vetted JVM crypto** (`crypto.JvmAead` / `JvmKdf` / `JvmSecureRandom`, jvmMain)
  and a **software hardware-stand-in** (`SoftwareSecretKeyProvider`, jvmTest) so the
  layer is fully unit-tested without a device.

### What to review (and why)
1. **Hardware-key handling.** Verify the Android Keystore / iOS Secure Enclave impls
   (sibling slices) actually create a non-exportable, hardware-bound AES-GCM key,
   use a fresh nonce per `encrypt`, and require user auth where intended. **This
   common layer cannot prove hardware isolation — it is verifiable only on-device.**
2. **Recovery wrap/unwrap.** Confirm the DEK is the only thing wrapped, both wraps
   recover the same DEK, the recovery code is generated from a CSPRNG and **never**
   serialized/logged/persisted (only its *wrap* is), and a wrong code is
   indistinguishable from a tamper (GCM tag failure — no oracle).
3. **No company-side key path (the core invariant).** Trace every persistence/
   transport path and confirm no third escrow/server-held key and no derivable code
   exists. The recovery path must remain user-only.
4. **AEAD / nonce usage.** AES-256-GCM, 96-bit random nonce per seal, 128-bit tag,
   `nonce || ct || tag` layout; AAD binding on the storage seam. Confirm no nonce
   reuse and that the JVM software keys are never used as a real provider.
5. **KDF parameters.** PBKDF2-HMAC-SHA256 @ 600k iters is the conservative default
   floor; assess whether Argon2id/scrypt (memory-hard) should be required for
   production, and review salt length/uniqueness. Params are self-describing in the
   blob so they can be raised without breaking existing wraps.
6. **Migration** of any data written by the Step-1 plaintext placeholder, plus
   backup/exclusion policy for the wrapped-key blob.

### Honesty / limitations
- The JVM impls hold the AES key as an in-memory `byte[]` — that is a **test/desktop
  stand-in**, not hardware isolation. Real key security depends on the on-device
  Keystore/Enclave impls and is **not verifiable in this shared layer**.
- This layer needs the human review above before **any** real user data is stored.

### ⚠️ Platform wiring vs. recovery-escrow gap — REVIEW + COMPLETE before real users
The shared `RecoveryManager` defines the canonical **dual-wrap escrow** (DEK
wrapped under BOTH the hardware key AND a recovery-code-derived key), which is
what enables **cross-device restore** with only the recovery code. **That full
escrow is not yet wired end-to-end on either platform.** What the platform slices
actually do today:
- **Android:** the encrypted store + `AndroidSecretKeyProvider` (Keystore
  AES-256-GCM) are wired into `AppContainer`. The first-run `RecoverySetupScreen`
  generates/displays/confirms a recovery code and persists a salted **PBKDF2
  verifier** (`onboarding/RecoveryCode`, `SecuritySetupRepository`) — it **captures
  and verifies** the code but does **not** yet escrow a re-derivable DEK off the
  device. So the keystore key is device-bound: lose the device → the code alone
  cannot currently restore data on a new device.
- **iOS:** `IosSecretKeyStore` (Secure Enclave + CryptoKit) + `RecoverySetupView`
  capture/confirm the code with their own `RecoveryCode` encoding; same gap.

**To complete (gated):** wire each platform's recovery path through the shared
`RecoveryManager` dual-wrap so the user-held code can actually restore the wallet
on a new device, then re-review. Until then the no-recovery warning copy ("lose
the device **and** the code → data is unrecoverable; the company cannot reset it")
is literally true, and cross-device recovery must not be promised to users.

### Tests (`:shared:jvmTest`, green)
`AeadAndProviderTest`, `EncryptedKeyValueStorageTest`, `RecoveryManagerTest`,
`RecoveryCodeTest`: encrypt→decrypt round-trip (incl. AAD, wrong-key, tamper);
delegate holds only ciphertext + key-binding swap fails; recovery wrap then unwrap
with correct code (success) / wrong code (fails) / hardware key; end-to-end recovery
on a fresh device; recovery codes high-entropy & unique.

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
- **Step 5:** Gate 1 moved NOT-BUILT → BUILT·PENDING-REVIEW. Shared crypto core
  (`crypto.SecretKeyProvider`, `EncryptedKeyValueStorage`, `RecoveryManager` /
  `WrappedDataKey`, `RecoveryCode`, vetted `JvmAead`/`JvmKdf`/`JvmSecureRandom`).
  Encrypts the whole local wallet at rest (AES-256-GCM); DEK dual-wrapped under the
  hardware key AND a high-entropy user-held recovery code (PBKDF2) — **no
  company-side key path**. Hardware isolation is verifiable only on-device (sibling
  Keystore/Enclave slices). Tested on `:shared:jvmTest`.
