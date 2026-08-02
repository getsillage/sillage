package app.sillage.ui.records

import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsProperties
import kotlin.test.Test
import kotlin.test.assertEquals

class SillageCalendarGridTest {
    @Test
    fun dayPresentationDerivesNumberAndCountLabel() {
        val populated = sillageCalendarDayPresentation(
            date = "2026-08-02",
            count = 3,
            isToday = true,
            selected = false,
        )
        val empty = sillageCalendarDayPresentation(
            date = "2026-08-09",
            count = 0,
            isToday = false,
            selected = true,
        )

        assertEquals("2", populated.dayNumber)
        assertEquals("3", populated.countLabel)
        assertEquals("9", empty.dayNumber)
        assertEquals(" ", empty.countLabel)
    }

    @Test
    fun daySemanticsExposeCompleteDescriptionAndSelectedState() {
        val semantics = SemanticsConfiguration()

        semantics.applySillageCalendarDaySemantics(
            description = "Aug 2, 2026, today, 2 records",
            isSelected = true,
        )

        assertEquals(
            listOf("Aug 2, 2026, today, 2 records"),
            semantics[SemanticsProperties.ContentDescription],
        )
        assertEquals(true, semantics[SemanticsProperties.Selected])
    }

    @Test
    fun unselectedDayPublishesFalseSelectedState() {
        val semantics = SemanticsConfiguration()

        semantics.applySillageCalendarDaySemantics(
            description = "Aug 3, 2026, 0 records",
            isSelected = false,
        )

        assertEquals(false, semantics[SemanticsProperties.Selected])
    }
}
