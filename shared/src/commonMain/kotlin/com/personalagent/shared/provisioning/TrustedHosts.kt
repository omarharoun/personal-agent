package com.personalagent.shared.provisioning

/**
 * The HTTPS-only trusted-source guard for model downloads.
 *
 * 🔒 SECURITY-CRITICAL. Two independent boundaries protect provisioning:
 *   1. Models come only from the curated [DefaultModelCatalog] — never from a
 *      user-typed URL (enforced by the contract: the UI hands back a [ModelOption],
 *      not a string).
 *   2. This guard: every URL actually fetched — the catalog URL **and every
 *      redirect hop** — must be `https://` and land on a host in [TRUSTED_HOSTS].
 *      Anything else is rejected before a byte is read. This blocks an SSRF /
 *      downgrade even if a catalog entry were mistyped or a trusted host tried to
 *      bounce the download to an untrusted origin.
 */
object TrustedHosts {
    /**
     * Registrable domains we trust to serve model weights. A request host matches
     * if it equals one of these or is a sub-domain of it — which covers Hugging
     * Face's resolve host (`huggingface.co`) and the CDN it 302-redirects to
     * (`*.hf.co`, e.g. `us.aws.cdn.hf.co`, `cdn-lfs.huggingface.co`).
     */
    val TRUSTED_HOSTS: Set<String> = setOf(
        "huggingface.co",
        "hf.co",
    )

    /** True when [host] is a trusted domain or a sub-domain of one. */
    fun isTrustedHost(host: String): Boolean {
        val h = host.lowercase().trimEnd('.')
        return TRUSTED_HOSTS.any { domain -> h == domain || h.endsWith(".$domain") }
    }

    /**
     * Validate a concrete URL about to be fetched. Returns null when allowed, or a
     * short, non-sensitive reason when it must be rejected.
     *
     * Requirements: scheme is exactly `https`, an explicit host is present, the
     * host is [isTrustedHost], and no embedded credentials (`user:pass@`) are used.
     */
    fun rejectionReason(url: String): String? {
        val trimmed = url.trim()
        val schemeSep = trimmed.indexOf("://")
        if (schemeSep <= 0) return "URL has no scheme"
        val scheme = trimmed.substring(0, schemeSep).lowercase()
        if (scheme != "https") return "non-HTTPS scheme '$scheme' is not allowed"

        var authority = trimmed.substring(schemeSep + 3)
        // Strip path/query/fragment to isolate the authority.
        val end = authority.indexOfFirst { it == '/' || it == '?' || it == '#' }
        if (end >= 0) authority = authority.substring(0, end)
        if (authority.isEmpty()) return "URL has no host"
        // Reject embedded credentials outright (they can disguise the real host).
        if (authority.contains('@')) return "URL must not contain embedded credentials"

        // Drop a :port suffix; keep IPv6 literals (`[...]`) intact.
        val host = when {
            authority.startsWith("[") -> authority.substringBefore(']').removePrefix("[")
            authority.contains(':') -> authority.substringBefore(':')
            else -> authority
        }
        if (host.isEmpty()) return "URL has no host"
        if (!isTrustedHost(host)) return "host '$host' is not in the trusted allowlist"
        return null
    }
}

/**
 * The lowercase-hex sentinel used for a catalog entry whose official SHA-256 has
 * not yet been pinned (typically a gated model the maintainer cannot read without
 * accepting the provider's license). It is a recognizable 64-char sentinel (it
 * embeds the word `pending`, so it is deliberately NOT valid hex), and
 * [KtorModelProvisioner] treats it as "unpinned" and **fails closed** — it will
 * never install an unverified blob.
 * Replace it with the publisher's published checksum to enable that entry.
 */
const val UNPINNED_SHA256: String =
    "00000000000000000000000000000000000000000000000000000000pending0"

/** True when [sha256] is a real, pinned 64-char lowercase-hex digest. */
internal fun isChecksumPinned(sha256: String): Boolean {
    if (sha256 == UNPINNED_SHA256) return false
    if (sha256.length != 64) return false
    return sha256.all { it in '0'..'9' || it in 'a'..'f' }
}
