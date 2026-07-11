package com.personalagent.shared.genui

import com.personalagent.shared.hermes.HermesClient
import com.personalagent.shared.hermes.HermesWireMessage

/**
 * Orchestrates one composition, end to end (docs/GENERATIVE_UI.md):
 *
 *   gather real [Facts] → build strict prompt → HermesClient.complete on an
 *   ISOLATED session id → lenient parse + reconcile → render, with graceful
 *   fallbacks the whole way down.
 *
 * The isolated [sessionId] (default `lifeagent-genui`) threads a separate
 * short-term transcript — exactly as the knowledge-graph extraction uses
 * `lifeagent-knowledge-extract` — so a heavyweight compose never pollutes the live
 * conversation's context. It shares the app-wide session *key*, so the same
 * long-term memory scope is in play (recall the session key vs id distinction).
 *
 * Composition is one more `complete()` call to the user's OWN Hermes — no new
 * backend, no new trust boundary. All model output is treated as untrusted data by
 * [ViewSpecParser]; every number is pinned to [Facts] before render.
 *
 * This class is deliberately thin (client + config) so it's easy to test with a
 * Ktor `MockEngine`; the (suspend) fact-gathering from the platform's stores is
 * done by the caller via [FactsCollector] and handed in as [Facts].
 */
class GenerativeUiService(
    private val client: HermesClient,
    private val sessionId: String = DEFAULT_SESSION_ID,
) {

    /**
     * Compose a view for [ask] against [facts]. Never throws:
     *  1. valid, reconciled model spec → [ComposeResult.Composed] (MODEL);
     *  2. model unreachable/junk but facts exist → local [DefaultView] (LOCAL);
     *  3. nothing to show → [ComposeResult.Prose] (plain agent line).
     */
    suspend fun compose(ask: String, facts: Facts): ComposeResult {
        if (facts.isEmpty()) {
            return ComposeResult.Prose(
                "There isn't much to show yet. As you chat, jot notes, set reminders and track goals, " +
                    "your views will fill in — everything here is built from your own data on this device.",
            )
        }

        val preferred = SuggestionChips.preferredViewFor(ask)
        val prompt = ViewSpecPrompts.build(facts, ask, preferred)
        val reply = runCatching {
            client.complete(listOf(HermesWireMessage(role = "user", content = prompt)), sessionId)
        }.getOrNull()

        val modelView = reply?.let { ViewSpecParser.parse(it, facts) }
        if (modelView != null) return ComposeResult.Composed(modelView)

        // Model failed or produced nothing usable — compose locally from real facts.
        DefaultView.build(facts)?.let { return ComposeResult.Composed(it) }

        return ComposeResult.Prose(
            if (reply == null) {
                "I can't reach your Hermes right now — I'll compose your view once you're connected again."
            } else {
                "I don't have enough to compose a view for that yet."
            },
        )
    }

    companion object {
        const val DEFAULT_SESSION_ID = "lifeagent-genui"
    }
}
