package app.sillage.ui.records

import app.sillage.features.records.CalendarMemoCoverage
import app.sillage.features.records.RecordsFeatureStateHolder
import app.sillage.features.records.RecordsPaginationStateHolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SillageCalendarCoverageNoticeTest {
    @Test
    fun presentationUsesCoverageAndSharedPaginationState() {
        val strings = SillageCalendarCoverageStrings(
            partialMonth = "Some records may not be loaded",
            completeMonth = "Earlier records are available",
            loadEarlierAction = "Load earlier records",
            loadingEarlierAction = "Loading earlier records",
        )
        val partial = sillageCalendarCoveragePresentation(
            state = RecordsFeatureStateHolder(),
            coverage = coverage(currentMonthMayBeIncomplete = true),
            strings = strings,
        )
        val loading = sillageCalendarCoveragePresentation(
            state = RecordsFeatureStateHolder(
                pagination = RecordsPaginationStateHolder(loadingMore = true),
            ),
            coverage = coverage(currentMonthMayBeIncomplete = false),
            strings = strings,
        )

        assertEquals("Some records may not be loaded", partial.message)
        assertEquals("Load earlier records", partial.actionLabel)
        assertFalse(partial.loading)
        assertEquals("Earlier records are available", loading.message)
        assertEquals("Loading earlier records", loading.actionLabel)
        assertTrue(loading.loading)
    }

    private fun coverage(currentMonthMayBeIncomplete: Boolean): CalendarMemoCoverage =
        CalendarMemoCoverage(
            hasMoreOlderRecords = true,
            currentMonthMayBeIncomplete = currentMonthMayBeIncomplete,
        )
}
