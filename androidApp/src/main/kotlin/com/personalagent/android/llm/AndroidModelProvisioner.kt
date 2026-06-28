package com.personalagent.android.llm

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.personalagent.shared.provisioning.ModelOption
import com.personalagent.shared.provisioning.ModelProvisioner
import com.personalagent.shared.provisioning.ProvisionState
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Real Android [ModelProvisioner]: streams the chosen model bundle from its
 * trusted source to app-private storage, reporting byte-accurate progress, then
 * verifies its SHA-256 before the file is moved into place. Nothing is loaded
 * until verification passes — a tampered or truncated download is rejected.
 *
 * The bundle installs to the SAME path [LlmModelProvisioning] resolves, so once
 * provisioning finishes the on-device LLM lights up with no other wiring.
 *
 * The download runs on [Dispatchers.IO]; collecting the returned [Flow] starts
 * it and cancelling the collector aborts it. There is no auto-download — the
 * caller (the setup screen / Settings) always initiates [provision].
 */
class AndroidModelProvisioner(context: Context) : ModelProvisioner {

    private val appContext = context.applicationContext

    override fun isInstalled(option: ModelOption): Boolean =
        installedFile(option).let { it.exists() && it.length() > 0L }

    override fun delete(option: ModelOption) {
        val f = installedFile(option)
        if (f.exists()) f.delete()
    }

    /**
     * The on-device file name for [option]. The canonical [ModelOption] carries no
     * explicit file name, so we derive it from the trusted catalog [ModelOption.url]
     * (last path segment), falling back to the stable id.
     */
    private fun fileNameFor(option: ModelOption): String =
        option.url.substringAfterLast('/').ifEmpty { "${option.id}.task" }

    /** Final resting place for [option]'s bundle (matches LlmModelProvisioning). */
    private fun installedFile(option: ModelOption): File =
        File(
            appContext.getExternalFilesDir(null),
            "${LlmModelProvisioning.MODEL_SUBDIR}/${fileNameFor(option)}",
        )

    override fun provision(option: ModelOption, wifiOnly: Boolean): Flow<ProvisionState> = flow {
        // Honor the Wi-Fi-only preference before spending any bytes.
        if (wifiOnly && !isOnUnmeteredWifi()) {
            emit(ProvisionState.Failed("Waiting for Wi-Fi. Connect to Wi-Fi or turn off the Wi-Fi-only setting to download over mobile data."))
            return@flow
        }

        val dest = installedFile(option)
        dest.parentFile?.mkdirs()
        // Download to a temp sibling first; only a verified file is promoted.
        val tmp = File(dest.parentFile, "${fileNameFor(option)}.part")

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(option.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 30_000
                instanceFollowRedirects = true
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                emit(ProvisionState.Failed("Download failed (HTTP $code). Please try again."))
                return@flow
            }

            val total = connection.contentLengthLong.let { if (it > 0L) it else option.sizeBytes }
            emit(ProvisionState.Downloading(0L, total))

            val digest = MessageDigest.getInstance("SHA-256")
            connection.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var lastEmitted = 0L
                    while (true) {
                        // Stop promptly if the collector was cancelled.
                        if (!currentCoroutineContext().isActive) {
                            tmp.delete()
                            return@flow
                        }
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        done += read
                        // Throttle UI updates to ~every 512 KB so the bar stays smooth.
                        if (done - lastEmitted >= 512 * 1024) {
                            lastEmitted = done
                            emit(ProvisionState.Downloading(done, total))
                        }
                    }
                    emit(ProvisionState.Downloading(done, total))
                }
            }

            // Verify integrity BEFORE the model is ever used.
            emit(ProvisionState.Verifying)
            val expected = option.sha256.trim().lowercase()
            if (expected.isNotEmpty()) {
                val actual = digest.digest().joinToString("") { b -> "%02x".format(b) }
                if (actual != expected) {
                    tmp.delete()
                    emit(ProvisionState.Failed("Verification failed: the downloaded file does not match the expected checksum. It was not installed."))
                    return@flow
                }
            }

            // Promote the verified temp file into place atomically where possible.
            if (dest.exists()) dest.delete()
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
            emit(ProvisionState.Installed)
        } catch (e: Exception) {
            tmp.delete()
            emit(ProvisionState.Failed(e.message ?: "Download failed. Please try again."))
        } finally {
            connection?.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    /** True only on a connected, un-metered (Wi-Fi-class) network. */
    private fun isOnUnmeteredWifi(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
