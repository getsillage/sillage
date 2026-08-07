package app.sillage.desktop

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DesktopClientSnapshotStorageTest {
    @Test
    fun replacesUtf8SnapshotAndLeavesNoTemporaryFile() {
        val directory = createTempDirectory("sillage-desktop-storage-")
        try {
            val path = directory.resolve("client-v1.json")
            val storage = DesktopClientSnapshotStorage(path)

            storage.write("{\"content\":\"\u4f60\u597d\"}")
            storage.write("{\"content\":\"updated\"}")

            assertEquals("{\"content\":\"updated\"}", storage.read())
            Files.list(directory).use { files ->
                assertFalse(files.anyMatch { it.fileName.toString().endsWith(".tmp") })
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun resolvesPlatformSpecificDataDirectories() {
        assertEquals(
            "/Users/example/Library/Application Support/Sillage/client-v1.json",
            DesktopDataPaths.defaultSnapshotPath(
                osName = "Mac OS X",
                userHome = "/Users/example",
                environment = emptyMap(),
            ).toString(),
        )
        assertEquals(
            "C:\\Users\\example\\AppData\\Roaming/Sillage/client-v1.json",
            DesktopDataPaths.defaultSnapshotPath(
                osName = "Windows 11",
                userHome = "C:\\Users\\example",
                environment = mapOf("APPDATA" to "C:\\Users\\example\\AppData\\Roaming"),
            ).toString(),
        )
    }

    @Test
    fun preservesOrAddsJsonBackupExtension() {
        assertEquals(Path.of("backup.json"), Path.of("backup.json").ensureJsonExtension())
        assertEquals(Path.of("backup.JSON"), Path.of("backup.JSON").ensureJsonExtension())
        assertEquals(Path.of("backup.json"), Path.of("backup").ensureJsonExtension())
    }
}
