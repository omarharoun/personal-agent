# Google Play Data Safety — draft answers

> **DRAFT — pending security review.** These are the proposed answers to Google
> Play's Data Safety questionnaire for Personal Agent, based on the app's current
> design. Confirm each answer against the shipping build (and after the 🔒 Gate
> reviews) before submitting — Data Safety answers are a binding declaration.

## Summary posture

Personal Agent is on-device-first. With cloud escalation **off (the default)**,
the app **collects no user data off the device** and shares nothing. The form
below covers both states and is written for the conservative (cloud-enabled) case
where relevant, since the form must describe what the app *can* do.

## Does your app collect or share any of the required user data types?

- **Default (cloud escalation off):** No data is collected or shared off-device.
- **If the user enables cloud escalation:** a minimized, anonymized request is
  sent to a configured third-party model provider to fulfill that request. This
  is **"App functionality"** processing, user-initiated, and not used for ads or
  analytics.

Declare the following so the listing is truthful about the cloud-on case:

### Data collected / shared

| Data type | Collected? | Shared? | Processed ephemerally? | Required? | Purpose |
|-----------|-----------|---------|------------------------|-----------|---------|
| App activity → user-generated content (the text of a question you escalate) | Collected only if you enable cloud escalation | Shared with the configured model provider when you escalate | Yes — sent to fulfill the request, intended zero-retention | Optional (feature is off by default) | App functionality |

Everything else — notes, reminders, plans, memory, embeddings, trusted contacts,
recovery code — is **not collected** (it stays on-device) and is **not shared**.

- **Location:** Not collected.
- **Personal info (name, email, address, phone, IDs):** Not collected by us. The
  user may type personal details into their own on-device content; the anonymizer
  attempts to strip such details from any escalated request, but this is
  best-effort (🔒 Gate 3) and the escalated text is therefore declared as
  user-generated content above.
- **Financial, health, contacts, calendar, photos, audio, files:** Not collected.
  (Trusted contacts are user-entered and stay on-device — not the device contact
  book.)
- **App info & performance / diagnostics / crash logs:** Not collected (no
  analytics or crash-reporting SDK).
- **Device or other IDs:** Not collected.

## Security questions

- **Is data encrypted in transit?** **Yes.** The only outbound data flow (cloud
  escalation) is HTTPS/TLS-only; a non-`https://` endpoint is rejected.
- **Is data encrypted at rest?** **Yes**, on-device, hardware-keystore-backed
  (🔒 pending review, Gate 1).
- **Can users request data deletion?** **Yes.** Data is on-device; users delete
  items in-app or uninstall to remove everything. There is no server-side store
  to issue a deletion request against.
- **Has your app's data collection been independently reviewed?** Not yet —
  declare honestly. (The 🔒 gates are internal-review-pending.)

## Notes for the submitter
- If you ship with cloud escalation **permanently disabled** for v1, you may be
  able to declare "no data collected/shared" — but only if the feature truly
  cannot be enabled in that build. As wired today it is **configurable**, so the
  conservative declaration above applies.
- Whatever provider is configured for escalation must itself be covered by your
  declaration and its own DPA / zero-retention terms.
