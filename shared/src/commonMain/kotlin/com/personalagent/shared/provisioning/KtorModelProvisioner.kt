// 🔒 SECURITY-CRITICAL — pending human security review; do not ship to a real
// user until reviewed. This file decides whether a downloaded blob is trusted
// enough to become the model the assistant runs. A mistake here installs an
// unverified or attacker-substituted model. The guarantees it must hold:
//   • HTTPS-only, trusted-host-only — for the catalog URL AND every redirect hop.
//   • Install ONLY bytes whose SHA-256 equals the pinned catalog checksum.
//   • On any failure/mismatch/cancel: discard the partial; never publish it.
package com.personalagent.shared.provisioning

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * The real, network-backed [ModelProvisioner], built on the Ktor multiplatform
 * client (engine injected per platform; tests pass `MockEngine`).
 *
 * Pipeline for [provision]:
 *  1. **Guard** — refuse a non-pinned checksum or a URL that isn't HTTPS on a
 *     trusted host (fail closed, before any byte is read).
 *  2. **Download** — stream the body, following redirects *manually* so each hop
 *     is re-validated against the same guard (SSRF / downgrade defense). Bytes go
 *     straight into a [StagedInstall] and an incremental [Sha256] — the file is
 *     never fully buffered in memory. Emit [ProvisionState.Downloading] with
 *     monotonic progress as chunks arrive.
 *  3. **Verify** — compare the streamed SHA-256 to the catalog checksum.
 *  4. **Install** — only on an exact match, [StagedInstall.commit] (atomic publish
 *     to the model path) → [ProvisionState.Installed]. Otherwise [abort] and
 *     [ProvisionState.Failed]; the model path keeps whatever (if anything) was
 *     there before.
 *
 * @param store where verified bytes land; [isInstalled]/[delete] delegate here.
 * @param engine Ktor engine (real per-platform engine in the app; `MockEngine` in tests).
 * @param maxRedirects hop cap for the manual redirect loop.
 * @param progressThrottleBytes emit a new [ProvisionState.Downloading] only after
 *   this many bytes have accumulated since the last emission (keeps the UI from
 *   being flooded); the final byte count is always emitted.
 */
class KtorModelProvisioner(
    private val store: ModelFileStore,
    engine: HttpClientEngine,
    private val maxRedirects: Int = 5,
    private val progressThrottleBytes: Long = 256 * 1024,
    connectTimeoutMs: Long = 15_000,
    requestTimeoutMs: Long = 0, // 0 = no whole-call cap; large models stream for a while
) : ModelProvisioner {

    private val configure: HttpClientConfig<*>.() -> Unit = {
        // We follow redirects ourselves so each hop can be re-validated.
        followRedirects = false
        install(HttpTimeout) {
            connectTimeoutMillis = connectTimeoutMs
            if (requestTimeoutMs > 0) requestTimeoutMillis = requestTimeoutMs
        }
        expectSuccess = false
    }

    private val client: HttpClient = HttpClient(engine, configure)

    override fun isInstalled(option: ModelOption): Boolean = store.isInstalled(option)

    override fun delete(option: ModelOption) = store.delete(option)

    override fun provision(option: ModelOption, wifiOnly: Boolean): Flow<ProvisionState> = flow {
        // --- 1. Guards (fail closed, before any network) ----------------------
        if (!isChecksumPinned(option.sha256)) {
            emit(ProvisionState.Failed("checksum for '${option.id}' is not pinned; refusing to install an unverified model"))
            return@flow
        }
        TrustedHosts.rejectionReason(option.url)?.let { reason ->
            emit(ProvisionState.Failed("rejected URL for '${option.id}': $reason"))
            return@flow
        }

        val staged = store.beginInstall(option)
        val sha = Sha256()
        val buf = ByteArray(CHUNK)
        var downloaded = 0L
        var lastEmitted = -1L
        // Allow a little overrun vs. advertised size before treating a stream as runaway.
        val ceiling = if (option.sizeBytes > 0) option.sizeBytes + SIZE_SLACK else Long.MAX_VALUE

        try {
            // --- 2. Manual redirect loop, re-validating every hop -------------
            var currentUrl = option.url.trim()
            var hops = 0
            while (true) {
                if (hops > 0) {
                    // Re-validate the redirect target before touching it.
                    TrustedHosts.rejectionReason(currentUrl)?.let { reason ->
                        staged.abort()
                        emit(ProvisionState.Failed("redirect for '${option.id}' rejected: $reason"))
                        return@flow
                    }
                }
                if (hops > maxRedirects) {
                    staged.abort()
                    emit(ProvisionState.Failed("too many redirects for '${option.id}'"))
                    return@flow
                }

                var redirectTo: String? = null
                var failure: String? = null

                client.prepareGet(currentUrl).execute { response: HttpResponse ->
                    when {
                        response.status.isRedirect() -> {
                            val location = response.headers[HttpHeaders.Location]
                            redirectTo = location?.let { resolveLocation(currentUrl, it) }
                            if (redirectTo == null) failure = "redirect with no Location header"
                        }

                        response.status.isSuccess() -> {
                            val total = response.contentLengthOrNull() ?: option.sizeBytes
                            val channel = response.bodyAsChannel()
                            while (true) {
                                currentCoroutineContext().ensureActive() // honor cancellation
                                val read = channel.readAvailable(buf, 0, buf.size)
                                if (read < 0) break // -1 = closed & drained
                                if (read == 0) continue
                                sha.update(buf, read)
                                staged.append(buf, read)
                                downloaded += read
                                if (downloaded > ceiling) {
                                    failure = "download exceeded expected size"
                                    return@execute
                                }
                                if (downloaded - lastEmitted >= progressThrottleBytes) {
                                    lastEmitted = downloaded
                                    emit(ProvisionState.Downloading(downloaded, maxOf(total, downloaded)))
                                }
                            }
                            // Always emit a final, exact progress point.
                            emit(ProvisionState.Downloading(downloaded, maxOf(total, downloaded)))
                        }

                        else -> {
                            val gatedHint = if (
                                response.status == HttpStatusCode.Unauthorized ||
                                response.status == HttpStatusCode.Forbidden
                            ) {
                                " — this model is gated; accept the provider license and supply a token"
                            } else {
                                ""
                            }
                            failure = "HTTP ${response.status.value}$gatedHint"
                        }
                    }
                }

                if (failure != null) {
                    staged.abort()
                    emit(ProvisionState.Failed("download failed for '${option.id}': $failure"))
                    return@flow
                }
                val next = redirectTo
                if (next != null) {
                    currentUrl = next
                    hops++
                    continue
                }
                break // reached a success body and finished streaming it
            }

            // --- 3. Verify -----------------------------------------------------
            emit(ProvisionState.Verifying)
            val actual = sha.digestHex()
            if (actual != option.sha256.lowercase()) {
                staged.abort()
                emit(ProvisionState.Failed("checksum mismatch for '${option.id}': expected ${option.sha256}, got $actual"))
                return@flow
            }

            // --- 4. Install (atomic) ------------------------------------------
            staged.commit()
            emit(ProvisionState.Installed)
        } catch (e: Throwable) {
            // Never leave a partial behind; never publish unverified bytes.
            staged.abort()
            // Re-throw cancellation so structured concurrency still works.
            currentCoroutineContext().ensureActive()
            emit(ProvisionState.Failed("provisioning error for '${option.id}': ${e::class.simpleName}"))
        }
    }

    /** Release the underlying engine. */
    fun close() = client.close()

    private companion object {
        private const val CHUNK = 64 * 1024
        private const val SIZE_SLACK = 4L * 1024 * 1024
    }
}

private fun HttpStatusCode.isRedirect(): Boolean = value in 300..399

private fun HttpResponse.contentLengthOrNull(): Long? =
    headers[HttpHeaders.ContentLength]?.toLongOrNull()

/**
 * Resolve a (possibly relative) `Location` against the URL it came from. Keeps an
 * absolute `https://…` as-is (it will be re-validated by the host allowlist);
 * resolves a root-relative `/path` and a bare relative path against the base
 * origin so a same-host redirect still works.
 */
internal fun resolveLocation(base: String, location: String): String {
    val loc = location.trim()
    if (loc.startsWith("https://", ignoreCase = true) || loc.startsWith("http://", ignoreCase = true)) {
        return loc
    }
    val schemeSep = base.indexOf("://")
    if (schemeSep <= 0) return loc
    val afterScheme = base.substring(schemeSep + 3)
    val originEnd = afterScheme.indexOfFirst { it == '/' || it == '?' || it == '#' }
    val origin = base.substring(0, schemeSep + 3 + (if (originEnd >= 0) originEnd else afterScheme.length))
    return if (loc.startsWith("/")) {
        origin + loc
    } else {
        origin + "/" + loc
    }
}
