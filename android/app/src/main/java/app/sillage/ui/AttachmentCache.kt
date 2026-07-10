package app.sillage.ui

import java.io.File

internal fun pruneAttachmentOpenCache(
    cacheRoot: File,
    nowMillis: Long = System.currentTimeMillis(),
    ttlMillis: Long = ATTACHMENT_OPEN_CACHE_TTL_MILLIS,
    minimumRetentionMillis: Long = ATTACHMENT_OPEN_CACHE_MINIMUM_RETENTION_MILLIS,
    maxRetainedEntries: Int = ATTACHMENT_OPEN_CACHE_MAX_RETAINED_ENTRIES,
) {
    if (!cacheRoot.isDirectory) {
        return
    }
    val ttl = ttlMillis.coerceAtLeast(0L)
    val minimumRetention = minimumRetentionMillis.coerceAtLeast(0L)
    cacheRoot.listFiles().orEmpty().forEach { entry ->
        if (entry.ageAt(nowMillis) >= ttl) {
            entry.deleteRecursively()
        }
    }

    var excess = cacheRoot.listFiles().orEmpty().size - maxRetainedEntries.coerceAtLeast(0)
    if (excess <= 0) {
        return
    }
    cacheRoot.listFiles().orEmpty()
        .sortedBy(File::lastModified)
        .forEach { entry ->
            if (excess > 0 && entry.ageAt(nowMillis) >= minimumRetention && entry.deleteRecursively()) {
                excess -= 1
            }
        }
}

private fun File.ageAt(nowMillis: Long): Long {
    return (nowMillis - lastModified()).coerceAtLeast(0L)
}

private const val ATTACHMENT_OPEN_CACHE_TTL_MILLIS = 24L * 60L * 60L * 1_000L
private const val ATTACHMENT_OPEN_CACHE_MINIMUM_RETENTION_MILLIS = 60L * 60L * 1_000L
private const val ATTACHMENT_OPEN_CACHE_MAX_RETAINED_ENTRIES = 8
