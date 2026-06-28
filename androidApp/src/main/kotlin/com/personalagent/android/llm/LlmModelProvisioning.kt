package com.personalagent.android.llm

import android.content.Context
import com.personalagent.shared.conversation.OnDeviceLlm
import java.io.File

/**
 * Single place the Android app obtains an [OnDeviceLlm], plus the rules for
 * where the (large, gitignored) model file lives.
 *
 * The on-device runtime is **MediaPipe LLM Inference**, which loads a `.task`
 * bundle. The curated [com.personalagent.shared.provisioning.DefaultModelCatalog]
 * downloads exactly those ungated `.task` bundles; the provisioner installs the
 * chosen one under [MODEL_SUBDIR] using its own file name. So instead of looking
 * for one hard-coded file name, we **resolve whichever `.task` bundle is present**
 * in the model directory (only one model is installed at a time).
 *
 * Resolution order:
 *  1. App external files dir — the `models/llm` folder under getExternalFilesDir,
 *     holding a `.task` bundle (the in-app downloader writes here).
 *  2. The MediaPipe sample convention — the `/data/local/tmp/llm` folder — handy
 *     during development (adb-push a `.task` bundle there).
 *
 * Use [isModelInstalled] to gate the feature: clones / fresh installs without a
 * model still build and run; [OnDeviceLlm.isAvailable] simply reports `false`.
 */
object LlmModelProvisioning {

    /** App-private subdirectory (under external files dir) holding the model. */
    const val MODEL_SUBDIR = "models/llm"

    /** The on-device runtime loads MediaPipe `.task` bundles. */
    const val MODEL_EXTENSION = ".task"

    /** Development drop location matching the MediaPipe LLM samples. */
    private const val DEV_PATH = "/data/local/tmp/llm"

    /** Creates the on-device LLM bound to the resolved model path. */
    fun create(context: Context): OnDeviceLlm =
        AndroidOnDeviceLlm(context.applicationContext, resolveModelFile(context))

    /** True only if a non-empty `.task` bundle exists at one of the known paths. */
    fun isModelInstalled(context: Context): Boolean =
        resolveModelFile(context).let { it.exists() && it.length() > 0L }

    /**
     * Resolves the installed model file: the first non-empty `*.task` bundle in
     * the app external-files model dir wins; otherwise the dev path. Returns a
     * non-existent placeholder in the recommended dir when neither has one (so
     * [isModelInstalled]/[OnDeviceLlm.isAvailable] report false and error copy
     * points at the recommended location).
     */
    fun resolveModelFile(context: Context): File {
        val externalDir = File(context.getExternalFilesDir(null), MODEL_SUBDIR)
        firstTaskBundle(externalDir)?.let { return it }
        firstTaskBundle(File(DEV_PATH))?.let { return it }
        // Nothing installed: point at a non-existent file in the recommended dir.
        return File(externalDir, "model$MODEL_EXTENSION")
    }

    /** The first non-empty `*.task` file directly inside [dir], or null. */
    private fun firstTaskBundle(dir: File): File? =
        dir.listFiles()
            ?.filter { it.isFile && it.length() > 0L && it.name.endsWith(MODEL_EXTENSION, ignoreCase = true) }
            ?.minByOrNull { it.name } // deterministic if several somehow present
}
