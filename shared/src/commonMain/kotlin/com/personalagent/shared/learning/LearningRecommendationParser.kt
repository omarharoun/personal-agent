package com.personalagent.shared.learning

import com.personalagent.shared.model.Ids
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Phase 6 Step 2 — parse the agent's strict-JSON recommendation reply into
 * sanitized, INERT [LearningResource]s.
 *
 * 🔒 REVIEW REQUIRED — untrusted web content. The agent's reply relays web
 * search/browse results. This parser treats every field as opaque text:
 *  - only `http(s)` URLs are kept (nothing that could launch another scheme),
 *  - control characters are stripped and lengths are capped,
 *  - the result is stored/rendered as data — never executed, never followed as
 *    instructions. The URL is opened only in the system browser by the UI layer.
 * We store just the link + title + one-sentence rationale — never fetched page
 * bodies (the "guide to the open web, don't re-host" boundary).
 */
object LearningRecommendationParser {

    /** DTO matching the JSON shape [LearningPrompts.recommendNext] asks for. */
    @Serializable
    private data class Dto(
        val title: String = "",
        val url: String = "",
        val source: String = "",
        val kind: String = "",
        val why: String = "",
        val concept: String? = null,
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * Parse [reply] into resources for [goalId]. Lenient: tolerates code fences and
     * surrounding prose by extracting the outermost JSON array. Returns at most
     * [max] sanitized resources; drops anything without a valid http(s) URL + title.
     */
    fun parse(reply: String, goalId: String, nowMillis: Long, max: Int = 3): List<LearningResource> {
        val array = extractArray(reply) ?: return emptyList()
        val dtos = runCatching { json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(Dto.serializer()), array) }
            .getOrDefault(emptyList())
        return dtos.asSequence()
            .mapNotNull { it.toResource(goalId, nowMillis) }
            .distinctBy { it.url.normalizedUrl() }
            .take(max)
            .toList()
    }

    private fun Dto.toResource(goalId: String, nowMillis: Long): LearningResource? {
        val cleanUrl = url.sanitize(500)
        if (!cleanUrl.startsWith("http://", true) && !cleanUrl.startsWith("https://", true)) return null
        val cleanTitle = title.sanitize(200).ifBlank { return null }
        return LearningResource(
            id = Ids.next(nowMillis),
            goalId = goalId,
            title = cleanTitle,
            url = cleanUrl,
            source = source.sanitize(80),
            kind = kind.toKind(),
            why = why.sanitize(300),
            concept = concept?.sanitize(60)?.ifBlank { null },
            status = LearningStatus.RECOMMENDED,
            recommendedAt = nowMillis,
            updatedAt = nowMillis,
        )
    }

    /** Strip control chars/newlines, collapse whitespace, cap length. */
    private fun String.sanitize(maxLen: Int): String =
        trim()
            .map { if (it.isISOControl()) ' ' else it }
            .joinToString("")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(maxLen)

    private fun String.toKind(): LearningKind = when (trim().lowercase()) {
        "video" -> LearningKind.VIDEO
        "article" -> LearningKind.ARTICLE
        "course" -> LearningKind.COURSE
        "docs", "documentation" -> LearningKind.DOCS
        "interactive" -> LearningKind.INTERACTIVE
        else -> LearningKind.OTHER
    }

    /** Extract the outermost `[ ... ]` so we tolerate ```json fences / stray prose. */
    private fun extractArray(reply: String): String? {
        val start = reply.indexOf('[')
        val end = reply.lastIndexOf(']')
        if (start < 0 || end <= start) return null
        return reply.substring(start, end + 1)
    }
}
