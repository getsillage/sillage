package app.sillage.ui.records

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SillageRecordEmptyStateTest {
    @Test
    fun presentationShowsActionOnlyWhenCallbackExists() {
        val actionable = sillageRecordEmptyStatePresentation(
            text = "Records failed to load",
            actionLabel = "Retry",
            hasAction = true,
        )
        val passive = sillageRecordEmptyStatePresentation(
            text = "No records",
            actionLabel = "Retry",
            hasAction = false,
        )

        assertEquals("Retry", actionable.actionLabel)
        assertNull(passive.actionLabel)
    }
}
