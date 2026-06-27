# Step 6 — Semantic cache: cache *understanding*, not replies

## The idea

A personal agent should get **cheaper and more personal at the same time**, and the
two should improve *together*, not as a trade-off. Step 6 makes that happen with a
semantic cache that stores **understanding** — the durable facts the system has
figured out about the user and the subjects they care about — rather than the
verbatim answers it has produced.

The flow for one turn:

```
turn ──► routing decision ──► cache lookup (semantic)
                                   │ hit ──────────────► answer locally  (cheap)
                                   │ miss ─► local / cloud ─► reply
                                            │
                                            └─► UnderstandingDistiller ─► store(topic, summary)
```

This slice owns two pieces (the `SemanticCache` implementation is owned by a
sibling — we only **populate** it through the agreed interface):

- **`UnderstandingDistiller(llm)`** — after a turn (especially one that hit the
  cloud or surfaced a new fact), it asks the on-device LLM to distill the
  interaction into a concise `topic` + `summary` of *understanding*, and stores it
  via `SemanticCache.store(topic, summary)`.
- **`CloudUsageStats` / `CloudUsageRecorder`** — telemetry that counts local-vs-cloud
  turns so the "cloud calls fall with use" property is measurable and observable.

## Why understanding, not replies

Caching verbatim replies is the obvious move and the wrong one:

- **Replies are answers to one phrasing of one question.** "What's my favorite
  coffee?" and "remind me my usual coffee order" want the *same fact* surfaced
  differently. A reply cache keyed on near-duplicate phrasings is brittle and
  stale; an understanding cache (`coffee preference → oat milk latte`) generalises.
- **A reply is the *output* of reasoning; understanding is the *input* to it.**
  Storing the fact lets the on-device model regenerate a fresh, in-context reply
  cheaply, instead of replaying a frozen sentence that may no longer fit.
- **Replies carry fluff and risk; facts are reusable.** Greetings, sign-offs, and
  the exact wording of a cloud answer aren't durable knowledge. The distiller's
  prompt explicitly treats the reply as *evidence only* and forbids copying it —
  it extracts what was learned, not what was said.

So the cache fills with things like *"User works primarily in Kotlin/KMP and
prefers terse answers,"* not *"Sure! Here's a terse answer in Kotlin…"*.

## Why cost and personalization improve together

They're the same mechanism viewed two ways:

- Every distilled fact is **one more thing the device knows**, so the next similar
  turn clears the cache-lookup bar and is **served locally** — a cloud call avoided.
- That same fact is also **one more thing the agent knows about *you***, so its
  local answers get more personal and better grounded.

More use ⇒ more understanding cached ⇒ **fewer cloud escalations** *and* **more
personal local answers**. Cost falls *because* personalization rises; they don't
trade off. `CloudUsageStats.cloudRatio` is the observable signal that this is
happening — it should drift down over a session as the cache learns.

## Design notes

- **On-device + deterministic-testable.** The distiller depends only on the
  `OnDeviceLlm` interface; tests inject the Step-3 `FakeOnDeviceLlm` to script the
  summary, so the whole path is provable in CI with **no model and no network**.
- **Fills with signal only.** Blank or trivially short turns are skipped before the
  model is even called, so the cache doesn't accumulate noise.
- **Low-temperature distillation.** Summarization runs at a low temperature —
  distilling facts should be stable, not creative.
- **Telemetry is a thin hook.** `CloudUsageRecorder` is the seam the sibling's
  `ConversationService` routing calls (`recordLocal()` on a cache hit / local turn,
  `recordCloud()` on escalation). The default `NoOpCloudUsageRecorder` keeps the
  service zero-cost and network-free when telemetry isn't wired.

## Measured: cloud usage falls with use

`CloudUsageStatsTest.cloudUsageFallsAsTheCacheLearns` simulates a session through a
tiny router that mirrors the real routing decision (lookup → hit = local, miss =
cloud + distill):

| Phase | What happens | cloud | local | cumulative cloud ratio |
|-------|--------------|------:|------:|-----------------------:|
| Cold  | 3 new questions, all miss | 3 | 0 | **1.00** |
| Warm  | same 3 questions asked 3× each → cache hits | 3 | 9 | **0.25** |

The warm phase adds **zero** cloud calls, and the cumulative cloud ratio falls from
`1.00` to `0.25` — strictly decreasing as the cache learns. That is the Step-6
property, asserted in test.

## Coordination / contract

- The `SemanticCache` / `CachedUnderstanding` declaration in
  `com.personalagent.shared.cache` is a **minimal copy of the agreed interface** so
  this worktree compiles standalone. It matches the contract verbatim
  (`store(topic, summary)`, `lookup(query, topK=3, minScore=0.6f)`, `clear()`).
  **Coordinator: dedup it against the sibling's canonical declaration before merge —
  keep exactly one.**
- The distiller constructor is `UnderstandingDistiller(llm)`; the cache is passed
  per call (`distillInto(cache, …)`) because the cache instance is owned and wired
  by the sibling/coordinator.
