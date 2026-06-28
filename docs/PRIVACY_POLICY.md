# Privacy Policy — Personal Agent (DRAFT)

> **DRAFT / NOT-FOR-REAL-USERS.** This describes how the app is *designed* to
> handle data. Several privacy-critical areas are still **pending human security
> review** (see `docs/SECURITY_REVIEW.md`, Gates 1–3) and must not be relied on
> by real users until that review and on-device verification are complete. Dates,
> contact details, and the governing entity are placeholders to fill in before
> publishing.

_Last updated: <FILL IN> · Contact: <FILL IN privacy contact email>_

## Eligibility — 18 and older only

**You must be 18 years of age or older to use Personal Agent.** The app and
service are intended for adults and are **not directed to children under 18**. We
do not knowingly allow under-18s to use the app: on first launch the app asks you
to confirm your date of birth, and anyone under 18 is blocked from proceeding. The
date of birth is checked on your device and is **not stored** — only a boolean
"18 or older, confirmed" flag is kept. We do not knowingly collect any data from
people under 18; the app holds no server-side data about anyone.

## The short version

Personal Agent is an **on-device** personal assistant. Your notes, reminders,
plans, and the agent's memory of you are stored **only on your device, encrypted
at rest**. We (the developer) run **no server** that holds your content. Nothing
about you is sold or shared for advertising. The only time any of your data
leaves the device is if **you** are using the optional cloud-escalation feature
(off by default), and even then only a **minimized, anonymized** version of a
single request is sent — see below.

## What data the app handles, and where it lives

| Data | Where it lives | Leaves the device? |
|------|----------------|--------------------|
| Notes, reminders, plan items | On-device, encrypted at rest | No |
| Agent memory + embeddings (vectors derived from your content) | On-device, encrypted at rest | No |
| Trusted contacts (name/relationship/phone you add) | On-device, encrypted at rest | No |
| Your encryption recovery code | Shown to you once; **never** stored by us | No |
| Reminders' local notifications | Scheduled on-device via the OS | No |

There are **no analytics SDKs, no ad SDKs, no crash-reporting upload, and no
account system**. The app does not read your contacts list, location, or
microphone.

## Encryption at rest

Your local data is encrypted with a key held in the device's hardware-backed
keystore (Android Keystore). Access can be recovered with a **user-held recovery
code** that only you possess — we cannot read your data and cannot reset or
recover it for you. (🔒 The encryption + recovery design is built but **pending
security review**; cross-device recovery is not yet complete — see
`docs/SECURITY_REVIEW.md`, Gate 1.)

## The one case data leaves the device: optional cloud escalation

The app can optionally send a hard question to a cloud AI model when the on-device
model can't answer it. This is **OFF by default** and only works if a cloud
provider is configured.

When it is on and a turn escalates:
- The outbound text is first **minimized** (we send as little as possible) and
  **anonymized** on your device (identifying details replaced with placeholder
  tokens). The mapping back to your real details **never leaves the device**.
- It is sent over **HTTPS (TLS)** to a model provider intended to be configured
  under a **zero-retention** agreement (no storage, no training on your data).
- The provider's answer is brought back and de-anonymized **on your device**.

(🔒 The anonymizer is best-effort, not a guarantee, and is **pending security
review** — see `docs/SECURITY_REVIEW.md`, Gate 3. It must not be relied on for
real personal data until that review is complete.)

We do not control third-party providers' independent practices; you should review
the policy of whichever provider is configured.

## Crisis-support feature

The app includes an optional, **consent-first** supportive surface and a
**trusted-contacts** list you curate yourself. The app **never contacts anyone on
your behalf automatically** — any call or message is something *you* initiate by
tapping, which opens your phone's dialer/SMS composer for you to send yourself.
There is no autonomous outreach. (🔒 This feature is **pending review by a
crisis-response expert** and is not for real users yet — `docs/SECURITY_REVIEW.md`,
Gate 2.) The app is **not** a crisis or medical service.

## Sharing and selling

We do **not** sell your data and do **not** share it for advertising or any
third-party purpose. The only outbound data flow is the optional, user-enabled
cloud escalation described above.

## Deleting your data

Because data is on-device, you can delete it at any time:
- Remove individual notes/reminders/plan items/contacts in the app.
- **Uninstalling the app** removes all of its on-device data, including the
  encrypted store. There is no server-side copy for us to delete.

## Children

The app is **for adults 18+ only** and is not directed to children (see
"Eligibility" above). It collects no personal data into any server. Set the app's
content rating to an adult/mature (18+) audience during Play submission and do not
enrol it in Google Play Families / Designed for Families.

## Changes

Material changes to this policy will be reflected here with an updated date
before they take effect.
