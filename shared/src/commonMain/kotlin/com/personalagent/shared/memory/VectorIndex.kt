package com.personalagent.shared.memory

/**
 * One hit from a similarity [VectorIndex.query]: the stored vector's [id], its
 * similarity [score] to the query (cosine, in `[-1, 1]`, higher = closer), and
 * the [metadata] it was upserted with.
 *
 * 🤝 SHARED CONTRACT — keep this shape exact across all three agents.
 */
data class VectorMatch(
    val id: String,
    val score: Float,
    val metadata: Map<String, String>,
)

/**
 * A nearest-neighbour store over fixed-length vectors.
 *
 * 🤝 SHARED CONTRACT — the platform siblings may later back this with a native
 * ANN library; the portable reference implementation ([InMemoryVectorIndex])
 * lives in `:shared` and is used everywhere until then. Callers (notably
 * [MemoryService]) depend ONLY on this interface.
 *
 * All operations are `suspend` so a disk- or native-backed implementation can do
 * real I/O without changing the contract.
 */
interface VectorIndex {
    /** Insert or replace the vector + metadata stored under [id]. */
    suspend fun upsert(id: String, vector: FloatArray, metadata: Map<String, String> = emptyMap())

    /** The [topK] vectors most similar to [vector], best first. */
    suspend fun query(vector: FloatArray, topK: Int): List<VectorMatch>

    /** Remove the vector stored under [id]; no-op if absent. */
    suspend fun delete(id: String)

    /** Drop every vector. */
    suspend fun clear()
}
