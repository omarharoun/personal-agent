# Knowledge map — derived from your conversations

A screen (drawer → **Knowledge**) that visualizes an interactive node-link graph of
the topics, entities and concepts you've explored in the app.

## Honest sourcing (read this first)

The graph is **derived from your saved chat records** — the on-device
[`ChatStore`](../shared/src/commonMain/kotlin/com/personalagent/shared/chat/ChatStore.kt).
It is **not** Hermes' server-side memory of you, and the screen never claims it is
(the header reads *"Derived from your conversations · not Hermes memory"*). It's a
best-effort model of what *you asked about in this app*, nothing more.

## How it's built

`KnowledgeGraphService.rebuild()` turns the local chat history into a
`{nodes, edges}` graph two ways, preferring the first:

1. **Model extraction (preferred).** Your own questions are sent to your Hermes via
   `POST /v1/chat/completions` with a strict structured-JSON prompt
   (`KnowledgeGraphExtractor.buildExtractionPrompt`) that asks for
   `{"nodes":[{id,label,type,weight}],"edges":[{from,to,relation}]}` and nothing
   else. The reply is parsed leniently (`parse` tolerates code fences / prose,
   drops edges whose endpoints don't resolve, clamps weights). This runs under an
   isolated session id (`lifeagent-knowledge-extract`) so it doesn't disturb your
   live chat's short-term context.
2. **Keyword fallback (offline).** If no model is reachable, `keywordFallback`
   derives nodes from term frequency across your messages (weight ≈ frequency) and
   edges from **co-occurrence within a conversation**, so the map still populates
   with no connection.

Either way, `attachSnippets` pins up to a few of your **real questions** to each
node, so tapping a node shows what you actually asked about it.

## Caching (stale-while-revalidate)

The result is cached, sealed-at-rest, in
[`KnowledgeGraphStore`](../shared/src/commonMain/kotlin/com/personalagent/shared/knowledge/KnowledgeGraphStore.kt).
The screen paints the cached graph **instantly** and only rebuilds when:

- you tap **Rebuild**, or
- the chat records have changed since the cache was built (a content
  `signature` mismatch — new chats accumulated), or
- the cache has aged past 12 h.

It never re-extracts on every screen open.

## Rendering

An interactive Compose `Canvas`:

- nodes are labelled dots, **sized by weight** and **coloured by type**
  (topic/entity/concept/person/place/activity/skill);
- edges are connecting lines;
- a small deterministic **Fruchterman–Reingold force layout** (pure Kotlin, seeded
  by node id) positions the graph so it reads well on a phone;
- **pan + pinch-zoom** to explore; **tap a node** for the questions you asked;
- node count is capped (~28–40) and the layout is O(n²·iters) with small n, so it
  stays smooth.

Honest empty state: *"Your knowledge map grows as you chat — ask a few things, then
rebuild."*
