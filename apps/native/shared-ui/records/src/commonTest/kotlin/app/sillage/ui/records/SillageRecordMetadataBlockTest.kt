package app.sillage.ui.records

import kotlin.test.Test
import kotlin.test.assertEquals

class SillageRecordMetadataBlockTest {
    @Test
    fun initialVersionHasNoRevisionHistory() {
        assertEquals(0, recordRevisionCount(1))
    }

    @Test
    fun laterVersionCountsPriorRevisions() {
        assertEquals(4, recordRevisionCount(5))
    }

    @Test
    fun invalidNonPositiveVersionDoesNotExposeNegativeHistory() {
        assertEquals(0, recordRevisionCount(0))
    }
}
