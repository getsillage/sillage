package app.sillage.ui.records

import app.sillage.core.domain.records.Memo
import kotlin.test.Test
import kotlin.test.assertEquals

class SillageOnThisDayCardTest {
    @Test
    fun presentationKeepsEntriesAndDerivesYearsAndExcerpts() {
        val first = memo(
            id = "first",
            entryDate = "2024-08-02",
            content = "A memory from two years ago",
        )
        val second = memo(
            id = "second",
            entryDate = "2025-08-02",
            content = "   ",
        )

        val presentation = sillageOnThisDayPresentation(
            entries = listOf(first, second),
            today = "2026-08-02",
            blankRecord = "Blank record",
        )

        assertEquals(listOf(first, second), presentation.entries.map { it.memo })
        assertEquals(listOf(2, 1), presentation.entries.map { it.yearsAgo })
        assertEquals("A memory from two years ago", presentation.entries.first().contentExcerpt)
        assertEquals("Blank record", presentation.entries.last().contentExcerpt)
    }

    private fun memo(
        id: String,
        entryDate: String,
        content: String,
    ): Memo = Memo(
        id = id,
        content = content,
        entryDate = entryDate,
        version = 1,
        createdAt = "2026-08-02T00:00:00Z",
        updatedAt = "2026-08-02T00:00:00Z",
        favoritedAt = null,
        archivedAt = null,
        deletedAt = null,
    )
}
