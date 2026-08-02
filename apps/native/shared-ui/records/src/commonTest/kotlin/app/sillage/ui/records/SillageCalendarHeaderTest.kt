package app.sillage.ui.records

import kotlin.test.Test
import kotlin.test.assertEquals

class SillageCalendarHeaderTest {
    @Test
    fun presentationKeepsNavigationDescriptionsOnTheirMatchingActions() {
        val presentation = sillageCalendarHeaderPresentation(
            SillageCalendarHeaderStrings(
                currentMonth = "August 2026",
                browseByDate = "Browse by date",
                previousMonthDescription = "July 2026",
                nextMonthDescription = "September 2026",
            ),
        )

        assertEquals("August 2026", presentation.currentMonth)
        assertEquals("Browse by date", presentation.browseByDate)
        assertEquals("July 2026", presentation.previousMonthDescription)
        assertEquals("September 2026", presentation.nextMonthDescription)
    }
}
