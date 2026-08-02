package app.sillage.ui.records

import app.sillage.core.domain.records.Memo
import app.sillage.features.records.RecordsBrowseStateHolder
import app.sillage.features.records.RecordsCollectionStateHolder
import app.sillage.features.records.RecordsFeatureStateHolder
import app.sillage.features.records.RecordsPaginationStateHolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SillageRecordCalendarTest {
    @Test
    fun presentationDerivesCountsSelectionAndCoverageNotice() {
        val selected = memo(id = "selected", entryDate = "2026-08-02")
        val another = memo(id = "another", entryDate = "2026-08-03")
        val presentation = sillageRecordCalendarPresentation(
            state = state(
                records = listOf(selected, another),
                selectedDate = selected.entryDate,
                nextCursor = "older",
            ),
        )

        assertEquals(mapOf("2026-08-02" to 1, "2026-08-03" to 1), presentation.counts)
        assertEquals(listOf(selected), presentation.selectedEntries)
        assertTrue(presentation.showCoverageNotice)
        assertFalse(presentation.showEmptySelection)
    }

    @Test
    fun presentationShowsEmptySelectionOnlyForSelectedDateWithoutRecords() {
        val selectedEmpty = sillageRecordCalendarPresentation(
            state = state(selectedDate = "2026-08-04"),
        )
        val noSelection = sillageRecordCalendarPresentation(
            state = state(selectedDate = null),
        )

        assertTrue(selectedEmpty.showEmptySelection)
        assertFalse(noSelection.showEmptySelection)
    }

    private fun state(
        records: List<Memo> = emptyList(),
        selectedDate: String? = null,
        nextCursor: String = "",
    ): RecordsFeatureStateHolder = RecordsFeatureStateHolder(
        collection = RecordsCollectionStateHolder(records = records),
        pagination = RecordsPaginationStateHolder(nextCursor = nextCursor),
        browse = RecordsBrowseStateHolder(
            calendarYear = 2026,
            calendarMonth = 8,
            selectedCalendarDate = selectedDate,
        ),
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
