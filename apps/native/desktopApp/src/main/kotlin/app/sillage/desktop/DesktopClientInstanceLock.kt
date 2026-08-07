package app.sillage.desktop

import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

internal class DesktopClientInstanceLock private constructor(
    private val channel: FileChannel,
    private val lock: FileLock,
) : AutoCloseable {
    private var closed = false

    override fun close() {
        if (closed) return
        closed = true
        try {
            lock.release()
        } finally {
            channel.close()
        }
    }

    companion object {
        fun tryAcquire(snapshotPath: Path): DesktopClientInstanceLock? {
            val lockPath = lockPath(snapshotPath)
            Files.createDirectories(requireNotNull(lockPath.parent))
            val channel = FileChannel.open(
                lockPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
            )
            val lock = try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            } catch (error: Exception) {
                channel.close()
                throw error
            }
            if (lock == null) {
                channel.close()
                return null
            }
            return DesktopClientInstanceLock(channel, lock)
        }

        internal fun lockPath(snapshotPath: Path): Path {
            val normalized = snapshotPath.toAbsolutePath().normalize()
            return normalized.resolveSibling("${normalized.fileName}.lock")
        }
    }
}
