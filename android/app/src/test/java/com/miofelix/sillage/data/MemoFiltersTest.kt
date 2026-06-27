package com.miofelix.sillage.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MemoFiltersTest {
    @Test
    fun sortMemosPinsFirstThenNewestEntryDate() {
        val oldPinned = memo(id = "old-pinned", entryDate = "2024-01-01", pinnedAt = "2024-01-04T00:00:00Z")
        val newest = memo(id = "newest", entryDate = "2024-01-03")
        val older = memo(id = "older", entryDate = "2024-01-02")

        val sorted = sortMemos(listOf(older, newest, oldPinned))

        assertEquals(listOf("old-pinned", "newest", "older"), sorted.map { it.id })
    }

    @Test
    fun activeMemosExcludesArchivedAndDeletedEntries() {
        val active = memo(id = "active")
        val archived = memo(id = "archived", archivedAt = "2024-01-02T00:00:00Z")
        val deleted = memo(id = "deleted", deletedAt = "2024-01-03T00:00:00Z")

        val filtered = activeMemos(listOf(archived, deleted, active))

        assertEquals(listOf("active"), filtered.map { it.id })
    }

    private fun memo(
        id: String,
        entryDate: String = "2024-01-01",
        pinnedAt: String? = null,
        archivedAt: String? = null,
        deletedAt: String? = null,
    ): Memo {
        return Memo(
            id = id,
            content = "content",
            entryDate = entryDate,
            version = 1,
            createdAt = "${entryDate}T00:00:00Z",
            updatedAt = "${entryDate}T00:00:00Z",
            pinnedAt = pinnedAt,
            archivedAt = archivedAt,
            deletedAt = deletedAt,
        )
    }
}
