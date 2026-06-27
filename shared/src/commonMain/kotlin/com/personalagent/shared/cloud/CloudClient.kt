package com.personalagent.shared.cloud

import com.personalagent.shared.conversation.GenOptions

/**
 * A remote ("cloud") language model — the **capability ceiling** the app can
 * escalate to when the on-device model is not enough.
 *
 * 🤝 SHARED CONTRACT — built to this EXACT shape by all Step 4 agents:
 *   - a sibling provides the portable, testable Fake/Stub implementation (test
 *     source) so the escalation orchestration is provable in CI with no network;
 *   - this transport agent provides the real [HttpCloudClient] (commonMain) that
 *     calls a frontier model over HTTPS under a zero-retention posture.
 *
 * The cloud is treated as a **stateless calculator**: one prompt in, one
 * completion out. No conversation state lives server-side; see `docs/CLOUD.md`.
 */
interface CloudClient {
    /** Human-readable label for this client (provider/model), for diagnostics. */
    val name: String

    /** Produce a single completion for [prompt] under [options]. */
    suspend fun complete(prompt: String, options: GenOptions = GenOptions()): String
}
