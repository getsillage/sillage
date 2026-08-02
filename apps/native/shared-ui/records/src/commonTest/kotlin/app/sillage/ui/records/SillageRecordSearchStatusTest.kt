package app.sillage.ui.records

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SillageRecordSearchStatusTest {
    @Test
    fun announcementOccursOnlyForANewCompletionEvent() {
        assertFalse(sillageRecordSearchStatusShouldAnnounce(4L, 4L))
        assertTrue(sillageRecordSearchStatusShouldAnnounce(4L, 5L))
    }
}
