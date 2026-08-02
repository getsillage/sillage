package app.sillage.ui.records

import app.sillage.features.records.CalendarMemoCoverage
import kotlin.test.Test
import kotlin.test.assertEquals

class SillageCalendarEmptySelectionTest {
    @Test
    fun messageFollowsSharedCalendarCoverage() {
        val strings = SillageCalendarEmptySelectionStrings(
            empty = "No records on this day",
            mayBeIncomplete = "Earlier records may not be loaded",
        )

        assertEquals(
            "No records on this day",
            sillageCalendarEmptySelectionMessage(
                coverage = coverage(currentMonthMayBeIncomplete = false),
                strings = strings,
            ),
        )
        assertEquals(
            "Earlier records may not be loaded",
            sillageCalendarEmptySelectionMessage(
                coverage = coverage(currentMonthMayBeIncomplete = true),
                strings = strings,
            ),
        )
    }

    private fun coverage(currentMonthMayBeIncomplete: Boolean): CalendarMemoCoverage =
        CalendarMemoCoverage(
            hasMoreOlderRecords = currentMonthMayBeIncomplete,
            currentMonthMayBeIncomplete = currentMonthMayBeIncomplete,
        )
}
