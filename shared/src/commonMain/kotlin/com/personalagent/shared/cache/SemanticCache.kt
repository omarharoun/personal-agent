package com.personalagent.shared.cache

import com.personalagent.shared.memory.Embedder
import com.personalagent.shared.store.KeyValueStorage
import com.personalagent.shared.util.Clock
import com.personalagent.shared.util.SystemClock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.math.sqrt

/**
 * One unit of accumulated **understanding** about a topic (or about the user):
 * a distilled [summary] of facts, NOT a verbatim reply. Carries the [embedding]
 * of `topic + summary` so it can be recalled by semantic similarity to a later
 * query, and [updatedAt] so the freshest understanding wins on ties.
 *
 * 🤝 SHARED CONTRACT — Step 6. The understanding-distiller + telemetry sibling
 * builds to this EXACT package and shape; keep it stable.
 */
data class CachedUnderstanding(
    val id: String,
    val topic: String,
    val summary: String,
    val embedding: FloatArray,
    val updatedAt: Long,
) {
    // data class + FloatArray ⇒ hand-rolled equality (array identity otherwise).
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CachedUnderstanding) return false
        return id == other.id &&
            topic == other.topic &&
            summary == other.summary &&
            updatedAt == other.updatedAt &&
            embedding.contentEquals(other.embedding)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + topic.hashCode()
        result = 31 * result + summary.hashCode()
        result = 31 * result + embedding.contentHashCode()
        result = 31 * result + updatedAt.hashCode()
        return result
    }
}

/**
 * The on-device **semantic cache of understanding**. Step 6's whole point: cache
 * the accumulated facts/summaries about topics and the user (NOT verbatim cloud
 * replies), and recall them by meaning so requests can be served locally —
 * cutting cloud usage as use accumulates while personalization deepens.
 *
 * 🤝 SHARED CONTRACT — Step 6. Three agents build to this EXACT package
 * (`com.personalagent.shared.cache`) and these EXACT signatures.
 */
interface SemanticCache {
    /** Distil + persist understanding about [topic] (embeds `topic + summary`). */
    suspend fun store(topic: String, summary: String)

    /**
     * Recall the understandings most relevant to [query]: cosine ≥ [minScore],
     * best-first, at most [topK], freshest-first on score ties.
     */
    suspend fun lookup(query: String, topK: Int = 3, minScore: Float = 0.6f): List<CachedUnderstanding>

    /** Drop all cached understanding. */
    suspend fun clear()
}

/**
 * A [SemanticCache] that does nothing: stores nothing, always misses. The safe
 * default wired into `ConversationService` so existing callers/tests behave
 * exactly as before Step 6 (no cache short-circuit) until a real cache is injected.
 */
object NoOpSemanticCache : SemanticCache {
    override suspend fun store(topic: String, summary: String) = Unit
    override suspend fun lookup(query: String, topK: Int, minScore: Float): List<CachedUnderstanding> =
        emptyList()
    override suspend fun clear() = Unit
}

/**
 * The portable reference [SemanticCache]: embeds understanding with the same
 * [Embedder] the memory engine uses and recalls it by cosine similarity, exactly
 * like [com.personalagent.shared.memory.InMemoryVectorIndex] — kept deliberately
 * small.
 *
 * Why this design:
 *   - **Portable & testable:** pure Kotlin, no native deps, runs on every KMP
 *     target and in JVM unit tests with the Step-2 [com.personalagent.shared.memory.HashingEmbedder].
 *   - **Encrypted at rest for free:** every mutation is written back through the
 *     [KeyValueStorage] seam, which in Step 5 became the encrypted wallet — so the
 *     cache inherits encryption with no change here.
 *   - **Upsert by topic:** the [id] is derived from a normalised [topic], so
 *     re-storing the same topic *updates* its understanding (and bumps
 *     [CachedUnderstanding.updatedAt]) rather than duplicating it.
 *
 * @param embedder turns `topic + summary` (and queries) into vectors. Same
 *   contract as the memory engine; the real on-device model drops in unchanged.
 * @param storage the persistence seam (the encrypted wallet in production).
 * @param clock supplies [CachedUnderstanding.updatedAt]; injectable for tests.
 * @param storageKey the single key the cache's JSON blob lives under.
 * @param json serializer (kept lenient/forward-compatible like the vector index).
 */
class EmbeddingSemanticCache(
    private val embedder: Embedder,
    private val storage: KeyValueStorage,
    private val clock: Clock = SystemClock,
    private val storageKey: String = DEFAULT_KEY,
    private val json: Json = DEFAULT_JSON,
) : SemanticCache {

    /** One persisted record. Vectors are stored as [List<Float>] for JSON. */
    @Serializable
    private data class Record(
        val id: String,
        val topic: String,
        val summary: String,
        val embedding: List<Float>,
        val updatedAt: Long,
    )

    private val mutex = Mutex()

    // Authoritative in-memory copy, keyed by id. Lazily hydrated from storage so
    // construction is cheap and reopening over the same storage is transparent.
    private val records = LinkedHashMap<String, Record>()
    private var loaded = false

    override suspend fun store(topic: String, summary: String): Unit = mutex.withLock {
        val t = topic.trim()
        val s = summary.trim()
        if (t.isEmpty() && s.isEmpty()) return@withLock // nothing to understand

        ensureLoaded()
        val id = idFor(t)
        // Embed the combined topic+summary so recall keys on the whole understanding.
        val vector = embedder.embed(combined(t, s))
        records[id] = Record(id, t, s, vector.toList(), clock.nowMillis())
        persist()
    }

    override suspend fun lookup(query: String, topK: Int, minScore: Float): List<CachedUnderstanding> =
        mutex.withLock {
            ensureLoaded()
            if (topK <= 0 || records.isEmpty()) return@withLock emptyList()

            val q = query.trim()
            if (q.isEmpty()) return@withLock emptyList()

            val queryVec = embedder.embed(q)
            val queryNorm = norm(queryVec)
            if (queryNorm == 0f) return@withLock emptyList() // a zero query matches nothing

            records.values
                .map { it to cosine(queryVec, queryNorm, it.embedding) }
                .filter { (_, score) -> score >= minScore }
                // score desc, then freshest-first on ties, then id for total order.
                .sortedWith(
                    compareByDescending<Pair<Record, Float>> { it.second }
                        .thenByDescending { it.first.updatedAt }
                        .thenBy { it.first.id },
                )
                .take(topK)
                .map { (r, _) -> r.toUnderstanding() }
        }

    override suspend fun clear(): Unit = mutex.withLock {
        ensureLoaded()
        if (records.isNotEmpty()) {
            records.clear()
            persist()
        }
    }

    /** Number of understandings currently held. Exposed for tests/diagnostics. */
    suspend fun size(): Int = mutex.withLock {
        ensureLoaded()
        records.size
    }

    // --- internals ---

    private fun Record.toUnderstanding(): CachedUnderstanding =
        CachedUnderstanding(id, topic, summary, embedding.toFloatArray(), updatedAt)

    /** Stable id per topic so re-storing the same topic upserts its understanding. */
    private fun idFor(topic: String): String {
        val key = topic.lowercase()
        return if (key.isEmpty()) "topic:" else "topic:$key"
    }

    private fun combined(topic: String, summary: String): String =
        when {
            topic.isEmpty() -> summary
            summary.isEmpty() -> topic
            else -> "$topic\n$summary"
        }

    private fun ensureLoaded() {
        if (loaded) return
        val raw = storage.get(storageKey)
        if (raw != null) {
            val stored = runCatching { json.decodeFromString(RECORD_LIST, raw) }.getOrDefault(emptyList())
            for (r in stored) records[r.id] = r
        }
        loaded = true
    }

    private fun persist() {
        storage.put(storageKey, json.encodeToString(RECORD_LIST, records.values.toList()))
    }

    /**
     * Cosine similarity between the query (whose norm is precomputed) and a
     * stored vector. Returns 0 for a zero-norm or length-mismatched record so a
     * malformed entry can never crash a lookup. Mirrors [InMemoryVectorIndex].
     */
    private fun cosine(query: FloatArray, queryNorm: Float, stored: List<Float>): Float {
        if (stored.size != query.size) return 0f
        var dot = 0f
        var storedSq = 0f
        for (i in query.indices) {
            val s = stored[i]
            dot += query[i] * s
            storedSq += s * s
        }
        val storedNorm = sqrt(storedSq)
        if (storedNorm == 0f) return 0f
        return dot / (queryNorm * storedNorm)
    }

    private fun norm(v: FloatArray): Float {
        var sq = 0f
        for (x in v) sq += x * x
        return sqrt(sq)
    }

    companion object {
        const val DEFAULT_KEY = "semantic_cache"

        private val RECORD_LIST = ListSerializer(Record.serializer())

        val DEFAULT_JSON = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
