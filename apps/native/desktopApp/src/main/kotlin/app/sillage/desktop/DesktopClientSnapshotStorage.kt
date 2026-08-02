package app.sillage.desktop

import app.sillage.core.localdata.ClientSnapshotStorage
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

internal class DesktopClientSnapshotStorage(
    private val snapshotPath: Path,
) : ClientSnapshotStorage {
    override val location: String = snapshotPath.toAbsolutePath().normalize().toString()

    override fun read(): String? {
        if (!Files.exists(snapshotPath)) return null
        return Files.readString(snapshotPath, StandardCharsets.UTF_8)
    }

    override fun write(value: String) {
        val parent = snapshotPath.toAbsolutePath().normalize().parent
        Files.createDirectories(parent)
        val temporary = Files.createTempFile(parent, "client-v1-", ".tmp")
        try {
            Files.newByteChannel(
                temporary,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING,
            ).use { channel ->
                channel.write(StandardCharsets.UTF_8.encode(value))
                if (channel is java.nio.channels.FileChannel) {
                    channel.force(true)
                }
            }
            try {
                Files.move(
                    temporary,
                    snapshotPath,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporary,
                    snapshotPath,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}

internal object DesktopDataPaths {
    fun defaultSnapshotPath(
        osName: String = System.getProperty("os.name"),
        userHome: String = System.getProperty("user.home"),
        environment: Map<String, String> = System.getenv(),
    ): Path {
        val directory = when {
            osName.contains("mac", ignoreCase = true) ->
                Path.of(userHome, "Library", "Application Support", "Sillage")
            osName.contains("win", ignoreCase = true) ->
                environment["APPDATA"]?.let(Path::of)
                    ?.resolve("Sillage")
                    ?: Path.of(userHome, "AppData", "Roaming", "Sillage")
            else -> environment["XDG_DATA_HOME"]?.let(Path::of)
                ?.resolve("Sillage")
                ?: Path.of(userHome, ".local", "share", "Sillage")
        }
        return directory.resolve("client-v1.json")
    }
}
