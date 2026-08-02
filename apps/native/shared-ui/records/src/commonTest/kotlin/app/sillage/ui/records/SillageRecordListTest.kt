package app.sillage.ui.records

import app.sillage.core.domain.records.Memo
import app.sillage.features.records.MemoListFilter
import app.sillage.features.records.RecordsBrowseStateHolder
import app.sillage.features.records.RecordsCollectionStateHolder
import app.sillage.features.records.RecordsFeatureStateHolder
import app.sillage.features.records.RecordsPaginationStateHolder
import app.sillage.features.records.RecordsSearchStateHolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SillageRecordListTest {
    @Test
    fun presentationUsesSearchingStateBeforeSearchEmptyState() {
        val presentation = sillageRecordListPresentation(
            state = state(
                search = RecordsSearchStateHolder(
                    query = "needle",
                    searching = true,
                ),
            ),
            today = "2026-08-02",
            strings = strings(),
        )

        assertEquals("Searching", presentation.emptyMessage)
        assertTrue(presentation.emptyUsesSearchIcon)
        assertTrue(presentation.visibleRecords.isEmpty())
        assertFalse(presentation.showLoadMore)
    }

    @Test
    fun presentationUsesFilterSpecificEmptyState() {
        val presentation = sillageRecordListPresentation(
            state = state(filter = MemoListFilter.Archived),
            today = "2026-08-02",
            strings = strings(),
        )

        assertEquals("No archived records", presentation.emptyMessage)
        assertFalse(presentation.emptyUsesSearchIcon)
        assertFalse(presentation.showingDeletedRecords)
    }

    @Test
    fun presentationOwnsOnThisDayAndPaginationState() {
        val memory = memo(id = "memory", entryDate = "2025-08-02")
        val current = memo(id = "current", entryDate = "2026-08-01")
        val presentation = sillageRecordListPresentation(
            state = state(
                records = listOf(memory, current),
                pagination = RecordsPaginationStateHolder(
                    nextCursor = "next",
                    loadingMore = true,
                ),
            ),
            today = "2026-08-02",
            strings = strings(),
        )

        assertEquals(listOf(memory, current), presentation.visibleRecords)
        assertEquals(listOf(memory), presentation.onThisDayEntries)
        assertTrue(presentation.showLoadMore)
        assertTrue(presentation.loadingMore)
    }

    @Test
    fun searchResultsSuppressOnThisDayAndPagination() {
        val result = memo(id = "result", entryDate = "2025-08-02")
        val presentation = sillageRecordListPresentation(
            state = state(
                records = listOf(result),
                search = RecordsSearchStateHolder(
                    query = "result",
                    results = listOf(result),
                    resultQuery = "result",
                ),
                pagination = RecordsPaginationStateHolder(nextCursor = "next"),
            ),
            today = "2026-08-02",
            strings = strings(),
        )

        assertEquals(listOf(result), presentation.visibleRecords)
        assertTrue(presentation.onThisDayEntries.isEmpty())
        assertFalse(presentation.showLoadMore)
    }

    private fun state(
        records: List<Memo> = emptyList(),
        search: RecordsSearchStateHolder = RecordsSearchStateHolder(),
        pagination: RecordsPaginationStateHolder = RecordsPaginationStateHolder(),
        filter: MemoListFilter = MemoListFilter.Unarchived,
    ): RecordsFeatureStateHolder = RecordsFeatureStateHolder(
        collection = RecordsCollectionStateHolder(records = records),
        search = search,
        pagination = pagination,
        browse = RecordsBrowseStateHolder(
            filter = filter,
            calendarYear = 2026,
            calendarMonth = 8,
        ),
    )

    private fun strings(): SillageRecordListStrings = SillageRecordListStrings(
        searching = "Searching",
        searchNoMatches = "No matches",
        emptyUnarchived = "No records",
        emptyArchived = "No archived records",
        emptyFavorited = "No favorites",
        emptyDeleted = "No deleted records",
        loadMore = "Load more",
        loadingMore = "Loading more",
    )

    private fun memo(id: String, entryDate: String): Memo = Memo(
        id = id,
        content = id,
        entryDate = entryDate,
        version = 1,
        createdAt = "2026-08-01T00:00:00Z",
        updatedAt = "2026-08-01T00:00:00Z",
        favoritedAt = null,
        archivedAt = null,
        deletedAt = null,
    )
}
