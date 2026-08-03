package app.sillage.desktop

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopClientInstanceLockTest {
    @Test
    fun excludesConcurrentOwnerAndCanBeReacquiredAfterClose() {
        val directory = createTempDirectory("sillage-desktop-lock-")
        try {
            val snapshotPath = directory.resolve("nested/client-v1.json")
            val first = assertNotNull(DesktopClientInstanceLock.tryAcquire(snapshotPath))
            try {
                assertNull(DesktopClientInstanceLock.tryAcquire(snapshotPath))
                val expectedLockPath = snapshotPath.toAbsolutePath().normalize()
                    .resolveSibling("client-v1.json.lock")
                assertEquals(expectedLockPath, DesktopClientInstanceLock.lockPath(snapshotPath))
                assertTrue(Files.isRegularFile(expectedLockPath))
            } finally {
                first.close()
            }

            assertNotNull(DesktopClientInstanceLock.tryAcquire(snapshotPath)).close()
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
