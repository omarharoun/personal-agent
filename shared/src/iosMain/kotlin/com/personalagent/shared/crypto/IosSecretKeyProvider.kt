package com.personalagent.shared.crypto

/**
 * 🔒 SECURITY-CRITICAL (Step 5 — encryption at rest, iOS). 🔒
 *
 * Synchronous, Swift-facing seam for the iOS secure key store, plus the Kotlin
 * adapter that exposes it as the shared [SecretKeyProvider].
 *
 * WHY a separate synchronous seam (mirrors `IosNativeLlm`/`IosLlmAdapter` from
 * Step 3 and `IosNativeEmbedder` from Step 2): the actual cryptography MUST run
 * in Swift against Apple's vetted frameworks — **CryptoKit** (AES-GCM),
 * **Security/Keychain**, and the **Secure Enclave** — none of which Kotlin/Native
 * should reimplement. We never roll our own crypto. So Swift implements this
 * plain synchronous interface and the Kotlin [IosSecretKeyProvider] simply
 * forwards calls; there is deliberately **no crypto in Kotlin** here — only
 * marshalling of `ByteArray`s across the bridge.
 *
 * Implemented in Swift by `IosSecretKeyStore`. See
 * `iosApp/iosApp/IosSecretKeyStore.swift`.
 *
 * Error model: [ensureKey]/[encrypt]/[decrypt] are `@Throws` so the Swift side
 * can surface Keychain/Secure-Enclave/AES-GCM failures (notably an
 * authentication failure on tampered ciphertext) as Kotlin exceptions, which
 * propagate up through [EncryptedKeyValueStorage] to the caller. We never return
 * unverified plaintext.
 */
interface IosNativeKeyStore {
    /** True once the data key (Keychain item / Secure-Enclave-wrapped) exists. */
    fun hasKey(): Boolean

    /** Idempotently create the device key if absent. */
    @Throws(Throwable::class)
    fun ensureKey()

    /**
     * AES-GCM seal via CryptoKit. Returns CryptoKit's "combined" representation
     * (`nonce ‖ ciphertext ‖ tag`) — a fresh 96-bit nonce per call. [aad] is
     * passed to GCM as additional authenticated data.
     */
    @Throws(Throwable::class)
    fun encrypt(plaintext: ByteArray, aad: ByteArray): ByteArray

    /**
     * AES-GCM open via CryptoKit. Throws on authentication failure (wrong key,
     * tampered bytes, or [aad] mismatch).
     */
    @Throws(Throwable::class)
    fun decrypt(ciphertext: ByteArray, aad: ByteArray): ByteArray
}

/**
 * Bridges the Swift [IosNativeKeyStore] to the shared [SecretKeyProvider].
 *
 * Pure forwarder — all key handling and cryptography live in Swift
 * (CryptoKit + Keychain + Secure Enclave). The synchronous calls are cheap
 * (AES-GCM over small JSON blobs; the data key is unwrapped once and cached on
 * the Swift side), so unlike the LLM/embedder adapters there is no need to shift
 * to a background dispatcher here.
 */
class IosSecretKeyProvider(
    private val native: IosNativeKeyStore,
) : SecretKeyProvider {

    override fun hasKey(): Boolean = native.hasKey()

    override fun ensureKey() = native.ensureKey()

    override fun encrypt(plaintext: ByteArray, aad: ByteArray): ByteArray =
        native.encrypt(plaintext, aad)

    override fun decrypt(ciphertext: ByteArray, aad: ByteArray): ByteArray =
        native.decrypt(ciphertext, aad)
}
