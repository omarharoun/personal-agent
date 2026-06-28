package com.personalagent.android.llm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Robust, **resumable** model download as a WorkManager foreground worker.
 *
 * Why a worker + foreground service: a model is large (~0.16–1.1 GB). Running the
 * download in-process tied to the UI meant backgrounding / locking / process death
 * killed it. WorkManager survives all three, and a foreground "Downloading model…"
 * notification keeps the OS from killing the work.
 *
 * Robustness:
 *  - **Resume** via HTTP `Range: bytes=<have>-` — a partial `.part` is continued,
 *    not restarted. A server that ignores Range (200 instead of 206) restarts cleanly.
 *  - **Verify** SHA-256 over the completed file before it is promoted into place;
 *    a mismatch fails closed (the `.part` is discarded, nothing is installed).
 *  - **Network drop / transient error → [Result.retry]** (with WorkManager backoff),
 *    which resumes from the kept `.part`. Only a checksum mismatch / bad input is a
 *    hard [Result.failure].
 *  - **Wi-Fi-only** is honored by the enqueue-time network constraint; if Wi-Fi
 *    drops the worker is stopped and rescheduled, then resumes.
 */
class ModelDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val url = inputData.getString(KEY_URL) ?: return@withContext fail("Missing download URL.")
        val sha = inputData.getString(KEY_SHA).orEmpty().trim().lowercase()
        val expectedSize = inputData.getLong(KEY_SIZE, 0L)
        val fileName = inputData.getString(KEY_FILE) ?: return@withContext fail("Missing file name.")

        val dir = File(applicationContext.getExternalFilesDir(null), LlmModelProvisioning.MODEL_SUBDIR)
            .apply { mkdirs() }
        val dest = File(dir, fileName)
        val tmp = File(dir, "$fileName.part")

        // If the verified file is already in place, we're done.
        if (dest.exists() && dest.length() > 0L && expectedSize > 0L && dest.length() == expectedSize) {
            return@withContext Result.success()
        }

        setForegroundSafe(0, expectedSize.coerceAtLeast(1L))

        var connection: HttpURLConnection? = null
        try {
            val have = if (tmp.exists()) tmp.length() else 0L
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                if (have > 0L) setRequestProperty("Range", "bytes=$have-")
            }
            val code = connection.responseCode

            // 416: the server says our .part is already the whole file → verify it.
            if (code == 416) {
                connection.disconnect()
                return@withContext verifyAndPromote(tmp, dest, sha)
            }
            if (code !in 200..299) {
                connection.disconnect()
                // Transient server/CDN hiccup → retry (resume kept).
                return@withContext Result.retry()
            }

            val resuming = code == HttpURLConnection.HTTP_PARTIAL // 206
            // If we asked to resume but the server sent the whole file (200), restart.
            if (have > 0L && !resuming) tmp.delete()
            val startAt = if (resuming) have else 0L
            val remaining = connection.contentLengthLong
            val total = when {
                resuming && remaining > 0L -> startAt + remaining
                remaining > 0L -> remaining
                else -> expectedSize
            }.coerceAtLeast(1L)

            connection.inputStream.use { input ->
                FileOutputStream(tmp, /* append = */ startAt > 0L).use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = startAt
                    var lastEmit = 0L
                    while (true) {
                        if (isStopped) {
                            // Cancelled or constraint lost: keep .part so we can resume.
                            return@withContext Result.retry()
                        }
                        val r = input.read(buf)
                        if (r < 0) break
                        output.write(buf, 0, r)
                        done += r
                        if (done - lastEmit >= 1_500_000L) {
                            lastEmit = done
                            setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to total, KEY_STAGE to STAGE_DOWNLOADING))
                            setForegroundSafe(done, total)
                        }
                    }
                }
            }
            connection.disconnect()
            connection = null

            return@withContext verifyAndPromote(tmp, dest, sha)
        } catch (t: Throwable) {
            connection?.disconnect()
            // Network drop / IO error → retry with backoff; .part is kept for resume.
            return@withContext Result.retry()
        }
    }

    private suspend fun verifyAndPromote(tmp: File, dest: File, expectedSha: String): Result {
        if (!tmp.exists() || tmp.length() == 0L) return Result.retry()
        setProgress(workDataOf(KEY_STAGE to STAGE_VERIFYING))
        if (isPinnedHex(expectedSha)) {
            val actual = sha256OfFile(tmp)
            if (actual != expectedSha) {
                tmp.delete()
                return fail("Verification failed: the file does not match the expected checksum. Nothing was installed.")
            }
        }
        if (dest.exists()) dest.delete()
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
        return Result.success()
    }

    private fun fail(reason: String): Result =
        Result.failure(workDataOf(KEY_REASON to reason))

    private fun sha256OfFile(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val r = input.read(buf)
                if (r < 0) break
                digest.update(buf, 0, r)
            }
        }
        return digest.digest().joinToString("") { b -> "%02x".format(b) }
    }

    private fun isPinnedHex(sha: String): Boolean =
        sha.length == 64 && sha.all { it in '0'..'9' || it in 'a'..'f' }

    private suspend fun setForegroundSafe(done: Long, total: Long) {
        runCatching { setForeground(foregroundInfo(done, total)) }
    }

    private fun foregroundInfo(done: Long, total: Long): ForegroundInfo {
        ensureChannel()
        val pct = if (total > 0L) ((done * 100) / total).toInt().coerceIn(0, 100) else 0
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading AI model…")
            .setContentText(if (total > 1L) "$pct%" else "Starting…")
            .setOngoing(true)
            .setProgress(100, pct, total <= 1L)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIF_ID, notification)
        }
    }

    private fun ensureChannel() {
        val mgr = applicationContext.getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Model download", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Progress of the on-device AI model download" },
            )
        }
    }

    companion object {
        const val UNIQUE_WORK = "model_download"
        const val CHANNEL_ID = "model_download"
        private const val NOTIF_ID = 4711

        const val KEY_URL = "url"
        const val KEY_SHA = "sha256"
        const val KEY_SIZE = "size"
        const val KEY_FILE = "file"
        const val KEY_OPTION_ID = "optionId"

        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_STAGE = "stage"
        const val KEY_REASON = "reason"

        const val STAGE_DOWNLOADING = "downloading"
        const val STAGE_VERIFYING = "verifying"
    }
}
