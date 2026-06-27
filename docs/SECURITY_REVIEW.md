# Security Review — gates & status

This file tracks the **🔒 HUMAN-REVIEW-REQUIRED gates** from the build brief.
A gate must be reviewed and signed off by a human before the corresponding
feature ships to real users. As of **Step 1**, both gates are **NOT BUILT** and
the surrounding placeholders are **NOT FOR REAL USERS**.

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

## Changelog
- **Step 1:** gates documented; both NOT-BUILT. Persistence is an explicit
  unencrypted placeholder behind `KeyValueStorage`.
