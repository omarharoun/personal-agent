package com.personalagent.shared.store

import java.io.File
import java.util.Properties

/**
 * Simple file-backed storage for the JVM target (desktop / CI / local runs).
 *
 * // TODO Step 5: swap for encrypted wallet. This writes plaintext to disk and
 * // exists so the shared logic is runnable off-device. Not for shipping user
 * // data.
 */
class FileKeyValueStorage(
    private val file: File,
) : KeyValueStorage {
    private val props = Properties().apply {
        if (file.exists()) file.inputStream().use { load(it) }
    }

    override fun get(key: String): String? = props.getProperty(key)

    override fun put(key: String, value: String) {
        props.setProperty(key, value)
        flush()
    }

    override fun remove(key: String) {
        props.remove(key)
        flush()
    }

    override fun keys(): Set<String> = props.stringPropertyNames().toSet()

    private fun flush() {
        file.parentFile?.mkdirs()
        file.outputStream().use { props.store(it, "personal-agent (UNENCRYPTED placeholder — Step 5)") }
    }
}
