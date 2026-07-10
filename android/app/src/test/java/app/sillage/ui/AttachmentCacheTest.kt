package app.sillage.ui

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentCacheTest {
    @Test
    fun expiredEntriesAreRemovedWhileFreshEntriesRemain() = withCacheRoot { root ->
        val expired = root.cacheEntry("expired", lastModified = 100L)
        val fresh = root.cacheEntry("fresh", lastModified = 950L)

        pruneAttachmentOpenCache(
            cacheRoot = root,
            nowMillis = 1_000L,
            ttlMillis = 500L,
            minimumRetentionMillis = 100L,
            maxRetainedEntries = 8,
        )

        assertFalse(expired.exists())
        assertTrue(fresh.isDirectory)
    }

    @Test
    fun oldestEntriesPastTheReadingWindowArePrunedToTheLimit() = withCacheRoot { root ->
        val oldest = root.cacheEntry("oldest", lastModified = 100L)
        val older = root.cacheEntry("older", lastModified = 200L)
        val recent = root.cacheEntry("recent", lastModified = 900L)
        val newest = root.cacheEntry("newest", lastModified = 950L)

        pruneAttachmentOpenCache(
            cacheRoot = root,
            nowMillis = 1_000L,
            ttlMillis = 2_000L,
            minimumRetentionMillis = 200L,
            maxRetainedEntries = 2,
        )

        assertFalse(oldest.exists())
        assertFalse(older.exists())
        assertTrue(recent.isDirectory)
        assertTrue(newest.isDirectory)
        assertEquals(2, root.listFiles().orEmpty().size)
    }

    @Test
    fun recentEntriesAreKeptEvenWhenTemporarilyAboveTheLimit() = withCacheRoot { root ->
        val first = root.cacheEntry("first", lastModified = 950L)
        val second = root.cacheEntry("second", lastModified = 975L)

        pruneAttachmentOpenCache(
            cacheRoot = root,
            nowMillis = 1_000L,
            ttlMillis = 2_000L,
            minimumRetentionMillis = 100L,
            maxRetainedEntries = 1,
        )

        assertTrue(first.isDirectory)
        assertTrue(second.isDirectory)
        assertEquals(2, root.listFiles().orEmpty().size)
    }

    private fun withCacheRoot(block: (File) -> Unit) {
        val root = Files.createTempDirectory("sillage-attachment-cache").toFile()
        try {
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun File.cacheEntry(name: String, lastModified: Long): File {
        return resolve(name).also { entry ->
            check(entry.mkdir())
            entry.resolve("attachment.bin").writeBytes(byteArrayOf(1))
            check(entry.setLastModified(lastModified))
        }
    }
}
