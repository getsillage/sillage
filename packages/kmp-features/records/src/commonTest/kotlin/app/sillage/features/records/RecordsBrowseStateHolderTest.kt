package app.sillage.features.records

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecordsBrowseStateHolderTest {
    @Test
    fun calendarModeUsesActiveRecordsWithoutChangingListChoiceOnReturn() {
        val archived = state().copy(filter = MemoListFilter.Archived)

        val calendar = archived.selectViewMode(MemoViewMode.Calendar)

        assertEquals(MemoListFilter.Unarchived, calendar.filter)
        assertEquals(MemoViewMode.Calendar, calendar.viewMode)
        assertEquals(MemoListFilter.Unarchived, calendar.selectViewMode(MemoViewMode.List).filter)
    }

    @Test
    fun restoredViewModePreservesCurrentFilter() {
        val archived = state().copy(filter = MemoListFilter.Archived)

        val restored = archived.restoreViewMode(MemoViewMode.Calendar)

        assertEquals(MemoViewMode.Calendar, restored.viewMode)
        assertEquals(MemoListFilter.Archived, restored.filter)
    }

    @Test
    fun monthChangeClearsDaySelection() {
        val changed = state().copy(selectedCalendarDate = "2026-08-01")
            .selectMonth(year = 2026, month = 9)

        assertEquals(2026, changed.calendarYear)
        assertEquals(9, changed.calendarMonth)
        assertNull(changed.selectedCalendarDate)
        assertEquals("2026-09-02", changed.selectCalendarDate("2026-09-02").selectedCalendarDate)
    }

    @Test
    fun persistedViewModeParsingFallsBackSafely() {
        assertEquals(MemoViewMode.Calendar, MemoViewMode.fromName("Calendar"))
        assertEquals(MemoViewMode.List, MemoViewMode.fromName("unknown"))
    }

    private fun state(): RecordsBrowseStateHolder {
        return RecordsBrowseStateHolder(
            calendarYear = 2026,
            calendarMonth = 8,
        )
    }
}
