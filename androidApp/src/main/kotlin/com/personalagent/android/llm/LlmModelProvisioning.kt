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

    /**
     * APK asset directory holding a BUNDLED `.task` model (provisioned at build
     * time by the `:androidApp:downloadLlmModel` Gradle task, gitignored). When
     * present, the on-device LLM works out of the box on a fresh install — no
     * download required. The bundle is copied to [MODEL_SUBDIR] on first use.
     */
    const val BUNDLED_ASSET_DIR = "models/llm"

    /** Creates the on-device LLM bound to the resolved (or bundled) model path. */
    fun create(context: Context): OnDeviceLlm {
        val ctx = context.applicationContext
        // 1) A real installed/adb-pushed bundle always wins.
        firstInstalledTask(ctx)?.let { return AndroidOnDeviceLlm(ctx, it) }
        // 2) Otherwise, if a `.task` is bundled in assets, target its copy location
        //    and hand the asset path to the LLM so it materializes on first use.
        bundledTaskAsset(ctx)?.let { asset ->
            val target = File(File(ctx.getExternalFilesDir(null), MODEL_SUBDIR), asset.substringAfterLast('/'))
            return AndroidOnDeviceLlm(ctx, target, bundledAssetPath = asset)
        }
        // 3) Nothing available — placeholder so isAvailable reports false cleanly.
        return AndroidOnDeviceLlm(ctx, resolveModelFile(ctx))
    }

    /** True if a `.task` is installed at a known path OR bundled in app assets. */
    fun isModelInstalled(context: Context): Boolean =
        firstInstalledTask(context) != null || bundledTaskAsset(context) != null

    /** The first installed `.task` (external files dir, then the dev path), or null. */
    private fun firstInstalledTask(context: Context): File? {
        val externalDir = File(context.getExternalFilesDir(null), MODEL_SUBDIR)
        firstTaskBundle(externalDir)?.let { return it }
        return firstTaskBundle(File(DEV_PATH))
    }

    /** The asset path of a bundled `.task` (e.g. `models/llm/Foo.task`), or null. */
    private fun bundledTaskAsset(context: Context): String? = try {
        context.assets.list(BUNDLED_ASSET_DIR)
            ?.firstOrNull { it.endsWith(MODEL_EXTENSION, ignoreCase = true) }
            ?.let { "$BUNDLED_ASSET_DIR/$it" }
    } catch (_: Throwable) {
        null
    }

    /**
     * Resolves the installed model file: the first non-empty `*.task` bundle in
     * the app external-files model dir wins; otherwise the dev path. Returns a
     * non-existent placeholder in the recommended dir when neither has one (so
     * error copy points at the recommended location).
     */
    fun resolveModelFile(context: Context): File {
        firstInstalledTask(context)?.let { return it }
        val externalDir = File(context.getExternalFilesDir(null), MODEL_SUBDIR)
        // Nothing installed: point at a non-existent file in the recommended dir.
        return File(externalDir, "model$MODEL_EXTENSION")
    }

    /** The first non-empty `*.task` file directly inside [dir], or null. */
    private fun firstTaskBundle(dir: File): File? =
        dir.listFiles()
            ?.filter { it.isFile && it.length() > 0L && it.name.endsWith(MODEL_EXTENSION, ignoreCase = true) }
            ?.minByOrNull { it.name } // deterministic if several somehow present
}
