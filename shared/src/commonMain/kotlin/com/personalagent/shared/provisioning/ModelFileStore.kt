package com.personalagent.shared.provisioning

/**
 * The narrow seam between "verified model bytes" and "where the model file lives
 * on this device" — the *model path*.
 *
 * Why a seam: [KtorModelProvisioner] is pure common code (download + hash +
 * progress) and must stay testable with no filesystem. The actual on-disk
 * placement is platform-specific (Android app files dir, iOS Application Support),
 * so each platform supplies a file-backed [ModelFileStore]; common code and tests
 * use [InMemoryModelFileStore].
 *
 * Install is **staged**: the provisioner writes streamed bytes to a temporary
 * staging area via [beginInstall] and only [StagedInstall.commit]s — the atomic
 * publish to the final model path — *after* the SHA-256 matches. A failure or
 * cancellation [StagedInstall.abort]s instead, so the final model path is only
 * ever occupied by a fully-downloaded, checksum-verified file. An unverified or
 * partial blob is never visible to [isInstalled] (and therefore never loaded).
 */
interface ModelFileStore {
    /** True when a committed, complete file exists at [option]'s model path. */
    fun isInstalled(option: ModelOption): Boolean

    /** Size in bytes of the committed file, or 0 if none is installed. */
    fun installedSize(option: ModelOption): Long

    /** Remove the committed file at [option]'s model path (no-op if absent). */
    fun delete(option: ModelOption)

    /** Begin a fresh staged install for [option], discarding any prior staging. */
    fun beginInstall(option: ModelOption): StagedInstall
}

/**
 * A single in-progress, staged model install. Bytes accumulate off to the side;
 * nothing reaches the live model path until [commit].
 */
interface StagedInstall {
    /** Append the first [length] bytes of [chunk] to the staged file. */
    fun append(chunk: ByteArray, length: Int)

    /** Bytes staged so far (monotonic). */
    fun stagedBytes(): Long

    /** Atomically publish the staged file to the final model path. */
    fun commit()

    /** Discard the staged file; the final model path is left untouched. */
    fun abort()
}

/**
 * In-memory [ModelFileStore] for common code and tests — no real filesystem.
 *
 * Models the same atomicity guarantee a real file store gives: staged bytes live
 * in a side buffer and only land in [installed] on [StagedInstall.commit].
 */
class InMemoryModelFileStore : ModelFileStore {
    private val installed = LinkedHashMap<String, ByteArray>()

    /** Test/diagnostic accessor: the committed bytes for [option], if any. */
    fun installedBytes(option: ModelOption): ByteArray? = installed[option.id]

    override fun isInstalled(option: ModelOption): Boolean = installed.containsKey(option.id)

    override fun installedSize(option: ModelOption): Long =
        installed[option.id]?.size?.toLong() ?: 0L

    override fun delete(option: ModelOption) {
        installed.remove(option.id)
    }

    override fun beginInstall(option: ModelOption): StagedInstall = InMemoryStagedInstall(option)

    private inner class InMemoryStagedInstall(private val option: ModelOption) : StagedInstall {
        private val staged = ArrayDeque<ByteArray>()
        private var bytes = 0L
        private var done = false

        override fun append(chunk: ByteArray, length: Int) {
            check(!done) { "staged install already finalized" }
            staged.addLast(chunk.copyOf(length))
            bytes += length
        }

        override fun stagedBytes(): Long = bytes

        override fun commit() {
            check(!done) { "staged install already finalized" }
            done = true
            val out = ByteArray(bytes.toInt())
            var offset = 0
            for (part in staged) {
                part.copyInto(out, offset)
                offset += part.size
            }
            staged.clear()
            installed[option.id] = out
        }

        override fun abort() {
            done = true
            staged.clear()
        }
    }
}
