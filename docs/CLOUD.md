# Cloud transport (Step 4 escalation) — zero-retention posture

The app is local-first. When the on-device model is not enough, the conversation
orchestration may **escalate** a single turn to a strong frontier model in the
cloud — the "capability ceiling." That escalation runs over the
`CloudClient` contract (`com.personalagent.shared.cloud.CloudClient`); the real
transport is `HttpCloudClient`, a thin HTTPS bridge built on the Ktor
multiplatform client.

This document records the **non-negotiable privacy posture** the transport is
built to. It is a posture, not just code: the strongest guarantees here are
**contractual** and must be verified in writing with the provider before any
real user data flows.

## The cloud is a stateless calculator

`HttpCloudClient` treats the remote model as a pure function: **one prompt in,
one completion out.** Concretely:

- **No server-side conversation state.** Each call is independent. We send the
  assembled prompt and nothing else; we do not rely on, or create, any
  provider-side session, thread, or history object.
- **No client-side echo into logs.** The client installs **no logging plugin**
  and never logs request or response bodies. Errors carry only status/shape
  (e.g. `HTTP 500`, `response too large`), never the prompt or completion.
- **Minimum headers.** Only `Authorization: Bearer …` and the JSON content type
  are sent. No telemetry, no custom tracing/echo headers that could carry
  context.

## Zero-retention is a CONTRACTUAL requirement

Code alone cannot guarantee the provider does not retain data. Before real user
data is ever sent, the provider **must** be configured under a written
zero-retention agreement that covers **both**:

1. **Storage** — prompts and completions are not persisted at rest beyond the
   minimum needed to return the response in-flight (ideally zero), and are not
   retained for abuse-monitoring windows without an explicit, agreed exception.
2. **Training** — prompts and completions are **never** used to train, fine-tune,
   or otherwise improve any model.

> **Operator checklist (verify in writing before go-live):**
> - [ ] Zero-retention covers storage **and** training, in the contract/DPA.
> - [ ] No human review / abuse-logging retention of payloads (or a documented,
>       accepted exception).
> - [ ] Data residency / sub-processor terms acceptable for the user base.
> - [ ] The API key used is scoped to the zero-retention configuration.

If any box is unchecked, the cloud path must stay disabled and the app falls
back to the on-device model.

## Transport hardening

`HttpCloudClient` is built to fail safe so the caller can always fall back to
local:

- **TLS only.** A non-`https://` base URL is rejected at construction
  (`IllegalArgumentException`). There is no plaintext path.
- **Timeouts.** Connect and whole-request budgets (defaults 10s / 30s); on expiry
  the call raises a clear `CloudException` rather than hanging.
- **Oversized guard.** Responses larger than `maxResponseChars` are rejected.
- **Uniform errors.** Non-2xx status, transport/TLS failure, timeout, malformed
  JSON, and empty completions all surface as `CloudException` — the escalation
  orchestration catches it and continues on-device.

## Configuration & secrets

`CloudConfig` carries `baseUrl` + `model` + `apiKey`. The **API key is supplied
at runtime** (secure storage / host-injected value) and is **never hardcoded or
committed**. The client only sends it as a bearer token over TLS and never logs
it. The provider is fully configurable (base URL + model + endpoint path), so a
zero-retention-configured endpoint can be pointed at without code changes.

## Engines per platform

The Ktor client core is in `commonMain`; the concrete engine is platform-specific:

| Target  | Engine  | Catalog dependency        |
|---------|---------|---------------------------|
| Android | OkHttp  | `ktor-client-okhttp`      |
| iOS     | Darwin  | `ktor-client-darwin`      |
| Tests   | MockEngine | `ktor-client-mock` (test) |

Unit tests use `MockEngine` — they assert the request is well-formed (HTTPS only,
correct `GenOptions` mapping, minimum headers, no payload in headers) and that
responses/errors are handled — with **no real network**.
