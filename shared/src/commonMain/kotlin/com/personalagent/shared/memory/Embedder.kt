package com.personalagent.shared.memory

/**
 * Turns text into a fixed-length vector for semantic search.
 *
 * 🤝 SHARED CONTRACT — three agents build to this exact signature. The portable
 * pieces ([HashingEmbedder], [MemoryService], the reference [VectorIndex]) live
 * here in `:shared`. The two platform siblings each provide a *real*,
 * on-device implementation of THIS interface (Android: a TFLite/ONNX sentence
 * embedder; iOS: a CoreML/NaturalLanguage embedder). Nothing above this
 * interface changes when the real models drop in.
 *
 * All implementations must be deterministic for a given input and must run
 * fully on-device with **no network**.
 */
interface Embedder {
    /** Length of every vector this embedder produces. Stable for its lifetime. */
    val dimension: Int

    /** Embed [text] into a [dimension]-length vector. */
    suspend fun embed(text: String): FloatArray
}

/**
 * A deterministic, dependency-free [Embedder] that makes the whole memory engine
 * unit-testable with **no model and no network**: it hashes a bag-of-words into
 * a fixed-dimension vector (the "feature hashing" / "hashing trick").
 *
 * This is NOT a semantic model — it has no notion of synonyms. But texts that
 * share words land near each other under cosine similarity, which is exactly
 * what the retrieval tests need: store a few notes, then a query that shares
 * vocabulary with one of them ranks that one first, every run, on every target.
 *
 * The real semantic embedders come from the platform siblings; this one exists
 * so [MemoryService] and the index are provable in CI without a device.
 *
 * Hashing uses a self-contained FNV-1a so token → bucket mapping is identical on
 * JVM, Android, and iOS (not reliant on platform [String.hashCode]). Signed
 * buckets reduce collision bias.
 */
class HashingEmbedder(
    override val dimension: Int = DEFAULT_DIMENSION,
) : Embedder {

    init {
        require(dimension > 0) { "dimension must be positive, was $dimension" }
    }

    override suspend fun embed(text: String): FloatArray {
        val vector = FloatArray(dimension)
        for (token in tokenize(text)) {
            val h = fnv1a(token)
            // Low bit picks the sign; the rest picks the bucket. Signed hashing
            // keeps collisions from systematically inflating one direction.
            val bucket = ((h shr 1) % dimension.toUInt()).toInt()
            val sign = if (h and 1u == 0u) 1f else -1f
            vector[bucket] += sign
        }
        return vector
    }

    /** Lowercase, split on any non-alphanumeric run. Empty tokens dropped. */
    private fun tokenize(text: String): List<String> =
        text.lowercase()
            .split(*NON_WORD)
            .filter { it.isNotEmpty() }

    /** 32-bit FNV-1a — small, fast, and identical across all KMP targets. */
    private fun fnv1a(s: String): UInt {
        var hash = 2166136261u
        for (ch in s) {
            hash = hash xor ch.code.toUInt()
            hash *= 16777619u
        }
        return hash
    }

    companion object {
        const val DEFAULT_DIMENSION = 256

        // Split on everything that isn't a letter/digit. Kept as a char array so
        // `split(vararg delimiters)` does a simple, allocation-light scan.
        private val NON_WORD: CharArray = buildString {
            for (c in ' '..'/') append(c)        // space, punctuation
            for (c in ':'..'@') append(c)        // : ; < = > ? @
            for (c in '['..'`') append(c)        // [ \ ] ^ _ `
            for (c in '{'..'~') append(c)        // { | } ~
            append('\n'); append('\t'); append('\r')
        }.toCharArray()
    }
}
