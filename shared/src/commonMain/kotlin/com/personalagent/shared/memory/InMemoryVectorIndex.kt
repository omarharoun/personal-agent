package com.personalagent.shared.memory

import com.personalagent.shared.store.KeyValueStorage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.math.sqrt

/**
 * The portable reference [VectorIndex]: an in-memory cosine-similarity store
 * whose state is persisted through the existing [KeyValueStorage] seam.
 *
 * Why this design:
 *   - **Portable & testable:** pure Kotlin, no native deps, runs on every KMP
 *     target and in JVM unit tests.
 *   - **Survives restart:** every mutation is written back through
 *     [KeyValueStorage], so a fresh index over the same storage sees prior
 *     vectors — the persistence contract the acceptance tests assert.
 *   - **Step-5 ready:** persistence goes through the SAME [KeyValueStorage] swap
 *     point as the rest of the app, so the encrypted-wallet implementation drops
 *     in unchanged — no caller or index change.
 *
 * ⚙️ v1 search is a **linear scan**: every [query] computes cosine against all
 * stored vectors. That's O(n·dimension) per query — fine for the thousands of
 * memories a single user accumulates (a few ms), and trivially correct. If a
 * user ever outgrows it, swap this implementation for an ANN-backed one behind
 * the same [VectorIndex] interface; nothing above changes.
 */
class InMemoryVectorIndex(
    private val storage: KeyValueStorage,
    private val storageKey: String = DEFAULT_KEY,
    private val json: Json = DEFAULT_JSON,
) : VectorIndex {

    /** One persisted record. Vectors are stored as [List<Float>] for JSON. */
    @Serializable
    private data class Record(
        val id: String,
        val vector: List<Float>,
        val metadata: Map<String, String> = emptyMap(),
    )

    private val mutex = Mutex()

    // Authoritative in-memory copy, keyed by id. Lazily hydrated from storage on
    // first use so construction is cheap and reopening is transparent.
    private val records = LinkedHashMap<String, Record>()
    private var loaded = false

    override suspend fun upsert(id: String, vector: FloatArray, metadata: Map<String, String>) =
        mutex.withLock {
            ensureLoaded()
            records[id] = Record(id, vector.toList(), metadata)
            persist()
        }

    override suspend fun query(vector: FloatArray, topK: Int): List<VectorMatch> = mutex.withLock {
        ensureLoaded()
        if (topK <= 0 || records.isEmpty()) return@withLock emptyList()

        val queryNorm = norm(vector)
        if (queryNorm == 0f) return@withLock emptyList() // a zero query matches nothing meaningfully

        records.values
            .map { record ->
                VectorMatch(
                    id = record.id,
                    score = cosine(vector, queryNorm, record.vector),
                    metadata = record.metadata,
                )
            }
            // Stable, deterministic ordering: score desc, then id for ties.
            .sortedWith(compareByDescending<VectorMatch> { it.score }.thenBy { it.id })
            .take(topK)
    }

    override suspend fun delete(id: String) = mutex.withLock {
        ensureLoaded()
        if (records.remove(id) != null) persist()
    }

    override suspend fun clear() = mutex.withLock {
        ensureLoaded()
        if (records.isNotEmpty()) {
            records.clear()
            persist()
        }
    }

    /** Number of vectors currently held. Exposed for tests/diagnostics. */
    suspend fun size(): Int = mutex.withLock {
        ensureLoaded()
        records.size
    }

    // --- internals ---

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
     * malformed entry can never crash a query.
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
        const val DEFAULT_KEY = "vector_index"

        private val RECORD_LIST = ListSerializer(Record.serializer())

        val DEFAULT_JSON = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
