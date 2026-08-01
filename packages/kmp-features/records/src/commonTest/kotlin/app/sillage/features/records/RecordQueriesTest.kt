package app.sillage.features.records

import app.sillage.core.domain.records.Memo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecordQueriesTest {
    @Test
    fun filtersRemainMutuallyExclusiveAndFavoritesIncludeArchivedRecords() {
        val unarchived = memo(id = "unarchived")
        val archived = memo(id = "archived", archivedAt = "2026-07-01T00:00:00Z")
        val favorite = memo(id = "favorite", favoritedAt = "2026-07-02T00:00:00Z")
        val archivedFavorite = memo(
            id = "archived-favorite",
            archivedAt = "2026-07-01T00:00:00Z",
            favoritedAt = "2026-07-02T00:00:00Z",
        )
        val deletedFavorite = memo(
            id = "deleted-favorite",
            favoritedAt = "2026-07-02T00:00:00Z",
            deletedAt = "2026-07-03T00:00:00Z",
        )
        val records = listOf(unarchived, archived, favorite, archivedFavorite, deletedFavorite)

        assertEquals(listOf("unarchived"), ids(memosForFilter(records, MemoListFilter.Unarchived)))
        assertEquals(listOf("archived"), ids(memosForFilter(records, MemoListFilter.Archived)))
        assertEquals(
            setOf("favorite", "archived-favorite"),
            ids(memosForFilter(records, MemoListFilter.Favorited)).toSet(),
        )
        assertEquals(listOf("deleted-favorite"), ids(memosForFilter(records, MemoListFilter.Deleted)))
    }

    @Test
    fun purgedRecordsNeverAppearInAnyList() {
        val purged = memo(
            id = "purged",
            deletedAt = "2026-07-03T00:00:00Z",
            purgedAt = "2026-08-01T00:00:00Z",
        )

        MemoListFilter.entries.forEach { filter ->
            assertFalse(purged.matchesListFilter(filter))
        }
    }

    @Test
    fun recordsSortByEntryDateThenCreationTime() {
        val records = listOf(
            memo(id = "older", entryDate = "2026-07-01"),
            memo(id = "same-day-first", createdAt = "2026-07-02T01:00:00Z"),
            memo(id = "same-day-last", createdAt = "2026-07-02T02:00:00Z"),
        )

        assertEquals(
            listOf("same-day-last", "same-day-first", "older"),
            ids(sortMemos(records)),
        )
    }

    @Test
    fun derivedQueriesUseOnlyActiveUnfavoritedRecords() {
        val first = memo(id = "first", entryDate = "2025-08-01")
        val older = memo(id = "older", entryDate = "2024-08-01")
        val favorite = memo(id = "favorite", entryDate = "2023-08-01", favoritedAt = "x")
        val archived = memo(id = "archived", entryDate = "2022-08-01", archivedAt = "x")
        val otherDay = memo(id = "other", entryDate = "2025-07-31")
        val records = listOf(first, older, favorite, archived, otherDay)

        assertEquals(listOf("first", "older"), ids(onThisDay(records, "2026-08-01")))
        assertEquals(1, entryDateCounts(records)["2025-08-01"])
        assertEquals(listOf("first"), ids(entriesByDate(records, "2025-08-01")))
    }

    @Test
    fun calendarCoverageTracksTheOldestLoadedActiveMonth() {
        val records = listOf(
            memo(id = "new", entryDate = "2026-07-08"),
            memo(id = "oldest", entryDate = "2026-06-30"),
            memo(id = "invalid", entryDate = "unknown"),
        )

        assertTrue(calendarMemoCoverage(records, "cursor", 2026, 6).currentMonthMayBeIncomplete)
        assertFalse(calendarMemoCoverage(records, "cursor", 2026, 7).currentMonthMayBeIncomplete)
        assertTrue(calendarMemoCoverage(records, "cursor", 2026, 5).currentMonthMayBeIncomplete)
        assertFalse(calendarMemoCoverage(records, "", 2026, 6).hasMoreOlderRecords)
    }

    @Test
    fun textHelpersPreserveFeatureSemantics() {
        assertEquals("one two…", excerpt(" one\n two three ", max = 7))
        assertEquals(3, yearsBetween("2023-08-01", "2026-08-01"))
    }

    private fun ids(records: List<Memo>): List<String> = records.map(Memo::id)

    private fun memo(
        id: String,
        entryDate: String = "2026-07-02",
        createdAt: String = "2026-07-02T00:00:00Z",
        favoritedAt: String? = null,
        archivedAt: String? = null,
        deletedAt: String? = null,
        purgedAt: String? = null,
    ): Memo {
        return Memo(
            id = id,
            content = id,
            entryDate = entryDate,
            version = 1,
            createdAt = createdAt,
            updatedAt = createdAt,
            favoritedAt = favoritedAt,
            archivedAt = archivedAt,
            deletedAt = deletedAt,
            purgedAt = purgedAt,
        )
    }
}
