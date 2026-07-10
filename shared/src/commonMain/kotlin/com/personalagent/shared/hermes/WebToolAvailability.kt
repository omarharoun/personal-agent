package com.personalagent.shared.hermes

/**
 * Phase 6 — detect whether the user's Hermes actually has a web-search backend
 * turned on, so the Learning Guide can tell the user "web search unavailable —
 * enable a backend on your Hermes" instead of failing silently.
 *
 * Verified live (Step 0): `GET /v1/toolsets` returns a `web` toolset with
 * `enabled` + `configured` flags and a `tools` list containing `web_search` /
 * `web_extract` when a backend (e.g. Browserbase) is provisioned. The `browser`
 * toolset also exposes `web_search`, so either satisfies the loop.
 */
object WebToolAvailability {

    /**
     * True when at least one enabled+configured toolset exposes a web-search tool.
     * Falls back to the toolset *name* (web/browser) if a Hermes build omits the
     * per-tool list.
     */
    fun isWebSearchAvailable(toolsets: List<HermesToolset>): Boolean =
        toolsets.any { ts ->
            ts.enabled && ts.configured && (
                ts.tools.any { it.equals(LearningPrompts.WEB_SEARCH_TOOL, ignoreCase = true) } ||
                    ts.name.equals("web", ignoreCase = true)
                )
        }

    /** Message shown when no web backend is configured (never fail silently). */
    const val UNAVAILABLE_MESSAGE: String =
        "Web search is unavailable on your Hermes. Enable a web-search backend " +
            "(e.g. set a search/browser provider key in your Hermes config) to get recommendations."
}
