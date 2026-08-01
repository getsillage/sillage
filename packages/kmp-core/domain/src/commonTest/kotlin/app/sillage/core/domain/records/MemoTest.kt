package app.sillage.core.domain.records

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MemoTest {
    @Test
    fun activeRecordHasNoArchiveDeletionOrPurgeMarker() {
        assertTrue(memo().isActive())
        assertFalse(memo(archivedAt = "2026-08-01T00:00:00Z").isActive())
        assertFalse(memo(deletedAt = "2026-08-01T00:00:00Z").isActive())
        assertFalse(memo(purgedAt = "2026-08-01T00:00:00Z").isActive())
    }

    private fun memo(
        archivedAt: String? = null,
        deletedAt: String? = null,
        purgedAt: String? = null,
    ): Memo {
        return Memo(
            id = "memo-1",
            content = "A private record",
            entryDate = "2026-08-01",
            version = 1,
            createdAt = "2026-08-01T00:00:00Z",
            updatedAt = "2026-08-01T00:00:00Z",
            favoritedAt = null,
            archivedAt = archivedAt,
            deletedAt = deletedAt,
            purgedAt = purgedAt,
        )
    }
}
