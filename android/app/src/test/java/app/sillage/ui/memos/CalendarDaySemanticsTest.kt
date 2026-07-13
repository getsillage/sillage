package app.sillage.ui.memos

import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsProperties
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarDaySemanticsTest {
    @Test
    fun calendarDayExposesItsCompleteDescriptionAndSelectedState() {
        val semantics = SemanticsConfiguration()

        semantics.applyCalendarDaySemantics(
            description = "Jul 14, 2026, today, 2 records",
            isSelected = true,
        )

        assertEquals(
            listOf("Jul 14, 2026, today, 2 records"),
            semantics[SemanticsProperties.ContentDescription],
        )
        assertEquals(true, semantics[SemanticsProperties.Selected])
    }

    @Test
    fun unselectedCalendarDayPublishesFalseSelectedState() {
        val semantics = SemanticsConfiguration()

        semantics.applyCalendarDaySemantics(
            description = "Jul 13, 2026, 0 records",
            isSelected = false,
        )

        assertEquals(false, semantics[SemanticsProperties.Selected])
    }
}
