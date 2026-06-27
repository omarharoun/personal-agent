package com.personalagent.android.llm

import android.content.Context
import com.personalagent.shared.llm.OnDeviceLlm
import java.io.File

/**
 * Single place the Android app obtains an [OnDeviceLlm], plus the rules for
 * where the (large, gitignored) model file lives.
 *
 * The `.task` weights (≈0.5–2 GB) are **never** committed to git and are too big
 * to ship inside the APK. They are loaded from a real file path on the device,
 * resolved in this order:
 *
 *  1. App external files dir — `<externalFilesDir>/models/llm/<MODEL_FILE>`.
 *     This is the recommended, app-private drop location. Provision it with:
 *
 *         adb push gemma3-1b-it-int4.task \
 *           /sdcard/Android/data/com.personalagent.android/files/models/llm/
 *
 *  2. The MediaPipe sample convention — `/data/local/tmp/llm/<MODEL_FILE>` —
 *     handy during development:
 *
 *         adb push gemma3-1b-it-int4.task /data/local/tmp/llm/
 *
 * Use [isModelInstalled] to gate the feature: clones / fresh installs without the
 * model still build and run; [OnDeviceLlm.isAvailable] simply reports `false`.
 */
object LlmModelProvisioning {

    /**
     * Default model bundle filename. **Gemma 3 1B int4** is the footprint pick;
     * swap to e.g. `llama-3.2-3b-it-int4.task` for quality. The real choice is a
     * measurement decision on the target device — see the project README.
     */
    const val MODEL_FILE = "gemma3-1b-it-int4.task"

    /** App-private subdirectory (under external files dir) holding the model. */
    const val MODEL_SUBDIR = "models/llm"

    /** Development drop location matching the MediaPipe LLM samples. */
    private const val DEV_PATH = "/data/local/tmp/llm"

    /** Creates the on-device LLM bound to the resolved model path. */
    fun create(context: Context): OnDeviceLlm =
        AndroidOnDeviceLlm(context.applicationContext, resolveModelFile(context))

    /** True only if a non-empty model bundle exists at one of the known paths. */
    fun isModelInstalled(context: Context): Boolean =
        resolveModelFile(context).let { it.exists() && it.length() > 0L }

    /**
     * Resolves the model file: the app external-files location wins if present,
     * otherwise the dev path. Returns the external-files candidate when neither
     * exists (so error messages point at the recommended location).
     */
    fun resolveModelFile(context: Context): File {
        val external = File(context.getExternalFilesDir(null), "$MODEL_SUBDIR/$MODEL_FILE")
        if (external.exists() && external.length() > 0L) return external
        val dev = File(DEV_PATH, MODEL_FILE)
        if (dev.exists() && dev.length() > 0L) return dev
        return external
    }
}
