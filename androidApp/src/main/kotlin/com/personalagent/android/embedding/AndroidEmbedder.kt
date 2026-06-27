package com.personalagent.android.embedding

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import com.personalagent.shared.memory.Embedder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.LongBuffer

/**
 * Real, fully on-device text [Embedder] for Android.
 *
 * Model: **all-MiniLM-L6-v2** (Sentence-Transformers) exported to ONNX, run with
 * **ONNX Runtime Mobile** (`com.microsoft.onnxruntime:onnxruntime-android`). It
 * produces 384-dim sentence embeddings entirely offline — no network, no server.
 *
 * Pipeline per [embed] call:
 *   1. WordPiece-tokenize the text ([BertTokenizer]) → `input_ids`.
 *   2. Run the transformer → per-token hidden states `last_hidden_state`.
 *   3. **Mean-pool** the token vectors using the attention mask.
 *   4. **L2-normalize** so dot product == cosine similarity.
 *
 * Heavy work (model load + inference) is confined to [Dispatchers.Default]. The
 * model + vocab are loaded lazily on first [embed] and reused thereafter; a
 * [Mutex] serializes access because a single [OrtSession] is not concurrent.
 *
 * The model weights (~90 MB) are **not** committed to git — see
 * [EmbedderFactory] and the project README for how the asset is provisioned.
 */
class AndroidEmbedder internal constructor(
    private val context: Context,
    private val modelAssetPath: String = "$ASSET_DIR/$MODEL_FILE",
    private val vocabAssetPath: String = "$ASSET_DIR/$VOCAB_FILE",
    private val maxSeqLen: Int = 256,
) : Embedder {

    override val dimension: Int = OUTPUT_DIM

    private val initMutex = Mutex()

    // Lazily-initialized, then immutable for the life of the embedder.
    @Volatile private var env: OrtEnvironment? = null
    @Volatile private var session: OrtSession? = null
    @Volatile private var tokenizer: BertTokenizer? = null

    override suspend fun embed(text: String): FloatArray = withContext(Dispatchers.Default) {
        val (sess, tok) = ensureLoaded()
        val ids = tok.encode(text)
        val seqLen = ids.size

        // attention_mask is all 1s (single, unpadded sequence); token_type_ids all 0.
        val mask = LongArray(seqLen) { 1L }
        val types = LongArray(seqLen) { 0L }
        val shape = longArrayOf(1, seqLen.toLong())

        val ortEnv = env!!
        val inputs = HashMap<String, OnnxTensor>()
        try {
            val wanted = sess.inputNames
            if ("input_ids" in wanted) {
                inputs["input_ids"] = OnnxTensor.createTensor(ortEnv, LongBuffer.wrap(ids), shape)
            }
            if ("attention_mask" in wanted) {
                inputs["attention_mask"] = OnnxTensor.createTensor(ortEnv, LongBuffer.wrap(mask), shape)
            }
            if ("token_type_ids" in wanted) {
                inputs["token_type_ids"] = OnnxTensor.createTensor(ortEnv, LongBuffer.wrap(types), shape)
            }

            sess.run(inputs).use { results ->
                // Prefer the named token-embedding output; otherwise the first
                // 3-D float output (shape [1, seq, dim]).
                val tokenEmb = pickTokenEmbeddings(results)
                meanPoolAndNormalize(tokenEmb, mask)
            }
        } finally {
            inputs.values.forEach { it.close() }
        }
    }

    /** Picks the `[1, seq, dim]` token-embedding tensor from the model outputs. */
    private fun pickTokenEmbeddings(results: OrtSession.Result): Array<FloatArray> {
        val named = results.get("last_hidden_state")
        val value = if (named.isPresent) named.get().value else results[0].value
        // Expected shape: [1, seq, dim] → batch 0.
        @Suppress("UNCHECKED_CAST")
        val batched = value as Array<Array<FloatArray>>
        return batched[0]
    }

    private fun meanPoolAndNormalize(tokens: Array<FloatArray>, mask: LongArray): FloatArray {
        val dim = OUTPUT_DIM
        val pooled = FloatArray(dim)
        var counted = 0
        for (t in tokens.indices) {
            if (mask[t] == 0L) continue
            counted++
            val row = tokens[t]
            for (d in 0 until dim) pooled[d] += row[d]
        }
        if (counted > 0) {
            val inv = 1f / counted
            for (d in 0 until dim) pooled[d] *= inv
        }
        // L2 normalize.
        var norm = 0f
        for (d in 0 until dim) norm += pooled[d] * pooled[d]
        norm = kotlin.math.sqrt(norm)
        if (norm > 1e-12f) {
            val inv = 1f / norm
            for (d in 0 until dim) pooled[d] *= inv
        }
        return pooled
    }

    private suspend fun ensureLoaded(): Pair<OrtSession, BertTokenizer> {
        session?.let { s -> tokenizer?.let { t -> return s to t } }
        return initMutex.withLock {
            session?.let { s -> tokenizer?.let { t -> return@withLock s to t } }

            val ortEnv = OrtEnvironment.getEnvironment()
            // ONNX Runtime memory-maps best from a real file; copy the asset out
            // of the (compressed) APK into app storage once.
            val modelFile = materializeAsset(modelAssetPath, MODEL_FILE)
            val opts = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(intraOpThreads())
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            val sess = ortEnv.createSession(modelFile.absolutePath, opts)
            val tok = context.assets.open(vocabAssetPath).use {
                BertTokenizer.fromVocab(it, maxSeqLen)
            }
            env = ortEnv
            session = sess
            tokenizer = tok
            sess to tok
        }
    }

    /**
     * Copies an asset into app storage once so ONNX Runtime can read it from a
     * real path. Re-copies only if missing/empty. (The `.onnx` asset is kept
     * uncompressed via `androidResources.noCompress` so it loads efficiently.)
     */
    private fun materializeAsset(assetPath: String, outName: String): File {
        val outDir = File(context.filesDir, ASSET_DIR).apply { mkdirs() }
        val outFile = File(outDir, outName)
        if (outFile.exists() && outFile.length() > 0L) return outFile
        context.assets.open(assetPath).use { input ->
            outFile.outputStream().use { output -> input.copyTo(output, bufferSize = 1 shl 16) }
        }
        return outFile
    }

    private fun intraOpThreads(): Int =
        Runtime.getRuntime().availableProcessors().coerceIn(1, 4)

    /** Releases native ONNX resources. Safe to call once when done. */
    fun close() {
        session?.close()
        session = null
        // OrtEnvironment is a process-wide singleton; do not close it here.
    }

    companion object {
        const val OUTPUT_DIM = 384
        const val ASSET_DIR = "models/all-MiniLM-L6-v2"
        const val MODEL_FILE = "model.onnx"
        const val VOCAB_FILE = "vocab.txt"
    }
}
