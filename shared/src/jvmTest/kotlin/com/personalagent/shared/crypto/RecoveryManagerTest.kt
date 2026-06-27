package com.personalagent.shared.crypto

import com.personalagent.shared.store.InMemoryKeyValueStorage
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Recovery dual-wrap: a DEK wrapped under BOTH the hardware key and a recovery code is
 * recoverable by EITHER, and by nothing else.
 */
class RecoveryManagerTest {

    // Low PBKDF2 cost so the suite stays fast; the wrapped blob carries its own params.
    private val fastKdf = KdfParams(iterations = 10_000)
    private fun manager() = RecoveryManager(JvmAead(), JvmKdf(), JvmSecureRandom(), fastKdf)

    @Test
    fun unwrap_with_hardware_recovers_the_dek() {
        val mgr = manager()
        val hw = SoftwareSecretKeyProvider()
        val dek = mgr.generateDataKey()
        val code = mgr.generateRecoveryCode()
        val wrapped = mgr.wrap(dek, hw, code)
        assertContentEquals(dek, mgr.unwrapWithHardware(wrapped, hw))
    }

    @Test
    fun unwrap_with_correct_recovery_code_recovers_the_dek() {
        val mgr = manager()
        val hw = SoftwareSecretKeyProvider()
        val dek = mgr.generateDataKey()
        val code = mgr.generateRecoveryCode()
        val wrapped = mgr.wrap(dek, hw, code)
        assertContentEquals(dek, mgr.unwrapWithRecoveryCode(wrapped, code))
    }

    @Test
    fun recovery_code_is_accepted_with_user_formatting() {
        val mgr = manager()
        val hw = SoftwareSecretKeyProvider()
        val dek = mgr.generateDataKey()
        val code = mgr.generateRecoveryCode()
        val wrapped = mgr.wrap(dek, hw, code)
        // User re-types with lowercase, extra spaces, and dropped dashes.
        val typed = code.lowercase().replace("-", "   ")
        assertContentEquals(dek, mgr.unwrapWithRecoveryCode(wrapped, typed))
    }

    @Test
    fun wrong_recovery_code_fails() {
        val mgr = manager()
        val hw = SoftwareSecretKeyProvider()
        val dek = mgr.generateDataKey()
        val code = mgr.generateRecoveryCode()
        val wrapped = mgr.wrap(dek, hw, code)
        val wrong = mgr.generateRecoveryCode()
        assertNotEquals(code, wrong)
        assertFailsWith<RecoveryException> { mgr.unwrapWithRecoveryCode(wrapped, wrong) }
    }

    @Test
    fun different_hardware_key_cannot_unwrap() {
        val mgr = manager()
        val hw = SoftwareSecretKeyProvider()
        val dek = mgr.generateDataKey()
        val wrapped = mgr.wrap(dek, hw, mgr.generateRecoveryCode())
        // A fresh device/key (company has no path to the original) cannot unwrap.
        assertFailsWith<RecoveryException> { mgr.unwrapWithHardware(wrapped, SoftwareSecretKeyProvider()) }
    }

    @Test
    fun tampered_recovery_wrap_fails() {
        val mgr = manager()
        val hw = SoftwareSecretKeyProvider()
        val code = mgr.generateRecoveryCode()
        val wrapped = mgr.wrap(mgr.generateDataKey(), hw, code)
        val bytes = Base64.decode(wrapped.recoveryWrap)
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 0x01).toByte()
        val tampered = wrapped.copy(recoveryWrap = Base64.encode(bytes))
        assertFailsWith<RecoveryException> { mgr.unwrapWithRecoveryCode(tampered, code) }
    }

    @Test
    fun blob_serializes_and_survives_json_roundtrip() {
        val mgr = manager()
        val hw = SoftwareSecretKeyProvider()
        val dek = mgr.generateDataKey()
        val code = mgr.generateRecoveryCode()
        val json = mgr.wrap(dek, hw, code).toJson()
        val restored = WrappedDataKey.fromJson(json)
        assertContentEquals(dek, mgr.unwrapWithRecoveryCode(restored, code))
        assertContentEquals(dek, mgr.unwrapWithHardware(restored, hw))
        assertTrue(!json.contains(code), "the recovery code must never be serialized into the blob")
    }

    @Test
    fun rotating_recovery_code_keeps_dek_and_invalidates_old_code() {
        val mgr = manager()
        val hw = SoftwareSecretKeyProvider()
        val dek = mgr.generateDataKey()
        val oldCode = mgr.generateRecoveryCode()
        val wrapped = mgr.wrap(dek, hw, oldCode)
        val (rotated, newCode) = mgr.rotateRecoveryCode(wrapped, oldCode, hw)
        assertNotEquals(oldCode, newCode)
        assertContentEquals(dek, mgr.unwrapWithRecoveryCode(rotated, newCode))
        assertFailsWith<RecoveryException> { mgr.unwrapWithRecoveryCode(rotated, oldCode) }
    }

    @Test
    fun end_to_end_recovery_decrypts_the_whole_store_on_a_new_device() {
        val mgr = manager()
        val aead = JvmAead()

        // --- Device 1: create DEK, wrap it, encrypt the wallet under the DEK. ---
        val hw1 = SoftwareSecretKeyProvider()
        val dek = mgr.generateDataKey()
        val code = mgr.generateRecoveryCode()
        val blob = mgr.wrap(dek, hw1, code)

        val disk = InMemoryKeyValueStorage()
        EncryptedKeyValueStorage(disk, AeadSecretKeyProvider(dek, aead)).apply {
            put("memory:1", "the user told me they live in Springfield")
            put("note:7", "buy milk")
        }

        // --- Device 2: hardware key is gone; recover the DEK from the user's code. ---
        val recoveredDek = mgr.unwrapWithRecoveryCode(blob, code)
        val onNewDevice = EncryptedKeyValueStorage(disk, AeadSecretKeyProvider(recoveredDek, aead))
        assertEquals("the user told me they live in Springfield", onNewDevice.get("memory:1"))
        assertEquals("buy milk", onNewDevice.get("note:7"))
    }
}
