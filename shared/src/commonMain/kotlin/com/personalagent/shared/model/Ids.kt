package com.personalagent.shared.model

/**
 * Stable, sortable-ish unique id generator that works on every KMP target
 * without pulling in a UUID dependency. Format: "<epochMillis>-<counter>".
 *
 * Ids are opaque strings everywhere else in the codebase — callers must not
 * parse them. If we later need RFC-4122 UUIDs we can swap this out freely.
 */
object Ids {
    private var counter: Long = 0L

    fun next(nowMillis: Long): String {
        counter += 1
        return "$nowMillis-$counter"
    }
}
