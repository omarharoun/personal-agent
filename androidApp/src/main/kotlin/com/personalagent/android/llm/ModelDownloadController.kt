package com.personalagent.android.llm

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.personalagent.shared.provisioning.ModelOption
import com.personalagent.shared.provisioning.ProvisionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Drives the robust, resumable model download via [ModelDownloadWorker] +
 * WorkManager, and exposes its state as a [ProvisionState] [Flow] the UI observes.
 *
 * Unlike collecting a download directly in a ViewModel coroutine (which dies when
 * the app is backgrounded), the work runs in a foreground service that survives
 * backgrounding, screen-lock, and process death. The UI merely *observes* it, so
 * the ViewModel/screen can come and go while the download keeps going.
 */
class ModelDownloadController(context: Context) {

    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)

    /** Start (or resume) downloading [option]. Existing work is replaced; the
     *  worker resumes from any partial `.part` via HTTP Range, so re-enqueuing the
     *  same model continues rather than restarts. */
    fun enqueue(option: ModelOption, wifiOnly: Boolean) {
        val fileName = option.url.substringAfterLast('/').ifEmpty { "${option.id}.task" }
        val data = workDataOf(
            ModelDownloadWorker.KEY_URL to option.url,
            ModelDownloadWorker.KEY_SHA to option.sha256,
            ModelDownloadWorker.KEY_SIZE to option.sizeBytes,
            ModelDownloadWorker.KEY_FILE to fileName,
            ModelDownloadWorker.KEY_OPTION_ID to option.id,
        )
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(data)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(
            ModelDownloadWorker.UNIQUE_WORK,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    /** Cancel the download and discard the partial file (reset to idle). */
    fun cancel() {
        workManager.cancelUniqueWork(ModelDownloadWorker.UNIQUE_WORK)
        // Discard any partial files so a future download starts fresh.
        File(appContext.getExternalFilesDir(null), LlmModelProvisioning.MODEL_SUBDIR)
            .listFiles()
            ?.filter { it.name.endsWith(".part") }
            ?.forEach { it.delete() }
    }

    /** The download's [ProvisionState], reflecting WorkManager's live work state. */
    fun stateFlow(fallbackTotal: Long = 0L): Flow<ProvisionState> =
        workManager.getWorkInfosForUniqueWorkFlow(ModelDownloadWorker.UNIQUE_WORK)
            .map { infos -> infos.firstOrNull().toProvisionState(fallbackTotal) }

    /** The option id of the currently in-flight/finished download, if any. */
    suspend fun currentOptionId(): String? =
        runCatching {
            workManager.getWorkInfosForUniqueWork(ModelDownloadWorker.UNIQUE_WORK).get()
                .firstOrNull()?.let { it.progress.getString(ModelDownloadWorker.KEY_OPTION_ID) }
        }.getOrNull()

    private fun WorkInfo?.toProvisionState(fallbackTotal: Long): ProvisionState {
        if (this == null) return ProvisionState.Idle
        return when (state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED ->
                // Waiting (e.g. for Wi-Fi) — show an indeterminate "starting" state.
                ProvisionState.Downloading(0L, fallbackTotal.coerceAtLeast(1L))
            WorkInfo.State.RUNNING -> {
                val stage = progress.getString(ModelDownloadWorker.KEY_STAGE)
                if (stage == ModelDownloadWorker.STAGE_VERIFYING) {
                    ProvisionState.Verifying
                } else {
                    val done = progress.getLong(ModelDownloadWorker.KEY_DONE, 0L)
                    val total = progress.getLong(ModelDownloadWorker.KEY_TOTAL, fallbackTotal)
                    ProvisionState.Downloading(done, total.coerceAtLeast(1L))
                }
            }
            WorkInfo.State.SUCCEEDED -> ProvisionState.Installed
            WorkInfo.State.FAILED ->
                ProvisionState.Failed(
                    outputData.getString(ModelDownloadWorker.KEY_REASON)
                        ?: "Download failed. Please try again.",
                )
            WorkInfo.State.CANCELLED -> ProvisionState.Idle
        }
    }
}
