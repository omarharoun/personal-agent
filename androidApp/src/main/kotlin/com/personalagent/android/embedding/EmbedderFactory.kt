package com.personalagent.android.embedding

import android.content.Context
import com.personalagent.shared.memory.Embedder
import java.io.IOException

/**
 * Single place the Android app obtains an [Embedder]. Keeps the concrete
 * [AndroidEmbedder] and its asset wiring out of [com.personalagent.android.AppContainer].
 *
 * The model weights are a runtime asset that is **not** committed to git (~90 MB).
 * Use [isModelInstalled] to check availability before relying on embeddings, and
 * see the project README ("On-device embeddings") for how to provision the asset.
 */
object EmbedderFactory {

    /** Creates the on-device embedder. Inference fails later if the model asset is absent. */
    fun create(context: Context): Embedder = AndroidEmbedder(context.applicationContext)

    /**
     * True only if both the model and vocab assets are present in the APK, so the
     * app can decide whether to enable semantic features or prompt to download.
     */
    fun isModelInstalled(context: Context): Boolean =
        assetExists(context, "${AndroidEmbedder.ASSET_DIR}/${AndroidEmbedder.MODEL_FILE}") &&
        assetExists(context, "${AndroidEmbedder.ASSET_DIR}/${AndroidEmbedder.VOCAB_FILE}")

    private fun assetExists(context: Context, path: String): Boolean = try {
        context.assets.open(path).use { true }
    } catch (_: IOException) {
        false
    }
}
