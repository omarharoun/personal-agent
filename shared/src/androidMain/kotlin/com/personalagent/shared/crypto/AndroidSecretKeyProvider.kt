package com.personalagent.shared.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 🔒 SECURITY-CRITICAL (Step 5) — Android encryption-at-rest key provider. 🔒
 *
 * Backs [SecretKeyProvider] with an **AES-256-GCM** key generated *inside* the
 * `AndroidKeyStore`. The raw key bytes never enter the app process or leave the
 * keystore — we only feed plaintext/ciphertext through a [Cipher] bound to the
 * key alias. On most modern devices the key lives in the TEE (hardware-backed);
 * where a secure element is present we request StrongBox (see below).
 *
 * Cipher: `AES/GCM/NoPadding`, 256-bit key, 96-bit IV, 128-bit auth tag.
 *   - GCM is AEAD: it authenticates both the ciphertext and the caller-supplied
 *     [aad]. Tampering (or a wrong AAD) fails the GCM tag check on decrypt.
 *   - **IV handling:** for keystore GCM keys, Android *requires the system to
 *     generate the IV* (a caller-supplied IV is rejected) precisely to prevent
 *     catastrophic IV reuse under one key. So [encrypt] inits the cipher without
 *     an IV, reads the fresh random IV the keystore generated, and prepends it.
 *     [decrypt] slices those first [IV_LENGTH] bytes back off. Each call → unique IV.
 *
 * StrongBox: where the device advertises a dedicated secure element
 * (`FEATURE_STRONGBOX_KEYSTORE`, API 28+), [setIsStrongBoxBacked] is requested
 * and we fall back to the TEE if the key gen throws [StrongBoxUnavailableException].
 *
 * setUserAuthenticationRequired — DELIBERATELY OFF here, documented decision:
 *   Turning it on would gate every decrypt behind a biometric/device-credential
 *   prompt (and bind the key to the lock screen). That is the right control for
 *   "unlock to reveal secrets" UX, but this store is read on app launch and in
 *   the background (reminders, memory), so a per-op auth prompt would break
 *   those paths. Tradeoff: data is protected at rest by the hardware key but is
 *   readable whenever the app process runs. A reviewer must decide whether
 *   sensitive subsets warrant a second, auth-bound key. See SECURITY_REVIEW Gate 1.
 *
 * 🔒 NOT-FOR-REAL-USERS until human security review (Gate 1). Hardware backing
 * and StrongBox availability can ONLY be confirmed on a real device.
 */
class AndroidSecretKeyProvider(
    private val keyAlias: String = DEFAULT_ALIAS,
) : SecretKeyProvider {

    private val keyStore: KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    override fun hasKey(): Boolean = keyStore.containsAlias(keyAlias)

    override fun ensureKey() {
        if (hasKey()) return
        generateKey()
    }

    override fun encrypt(plaintext: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // No IV supplied: the AndroidKeyStore generates a fresh random IV.
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        if (aad.isNotEmpty()) cipher.updateAAD(aad)
        val iv = cipher.iv
        require(iv.size == IV_LENGTH) { "Unexpected IV length ${iv.size}" }
        val body = cipher.doFinal(plaintext)
        // [ IV (12) | ciphertext+tag ]
        return iv + body
    }

    override fun decrypt(ciphertext: ByteArray, aad: ByteArray): ByteArray {
        require(ciphertext.size > IV_LENGTH) { "Ciphertext too short to contain IV" }
        val iv = ciphertext.copyOfRange(0, IV_LENGTH)
        val body = ciphertext.copyOfRange(IV_LENGTH, ciphertext.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        if (aad.isNotEmpty()) cipher.updateAAD(aad)
        return cipher.doFinal(body)
    }

    private fun secretKey(): SecretKey {
        val entry = keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
            ?: error("Keystore entry '$keyAlias' missing or not a secret key; call ensureKey() first")
        return entry.secretKey
    }

    private fun generateKey() {
        val strongBoxAvailable =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && hasStrongBox()
        try {
            buildKey(strongBox = strongBoxAvailable)
        } catch (e: StrongBoxUnavailableException) {
            // Secure element advertised but refused this key spec — fall back to TEE.
            buildKey(strongBox = false)
        }
    }

    private fun buildKey(strongBox: Boolean) {
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            // We let the keystore pick/generate the IV (randomized encryption) so
            // a caller can never force IV reuse. Required for the encrypt() flow above.
            .setRandomizedEncryptionRequired(true)
            // Documented: see class KDoc. Off so background reads work without a prompt.
            .setUserAuthenticationRequired(false)
            .apply {
                if (strongBox) setIsStrongBoxBacked(true)
            }
            .build()
        generator.init(spec)
        generator.generateKey()
    }

    private fun hasStrongBox(): Boolean {
        // The shared module has no Context, so we don't query
        // PackageManager.FEATURE_STRONGBOX_KEYSTORE here. Instead we optimistically
        // request StrongBox on API 28+ and rely on the StrongBoxUnavailableException
        // fallback in generateKey() as the real guarantee: if the device has no
        // secure element, key gen throws and we transparently retry on the TEE.
        return true
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val DEFAULT_ALIAS = "personal_agent_master_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BITS = 256
        private const val IV_LENGTH = 12          // 96-bit GCM IV (NIST-recommended)
        private const val TAG_BITS = 128          // GCM auth tag length in bits
    }
}
