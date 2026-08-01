package app.sillage.features.records

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RecordsAttachmentOpenStateHolderTest {
    @Test
    fun beginClaimsOneNonBlankPath() {
        val idle = RecordsAttachmentOpenStateHolder(requestId = 4)

        val opening = checkNotNull(idle.begin("/attachments/file-1"))

        assertEquals("/attachments/file-1", opening.path)
        assertEquals(5L, opening.requestId)
        assertTrue(opening.opening)
        assertTrue(opening.owns(5))
        assertFalse(opening.owns(4))
        assertNull(opening.begin("/attachments/file-2"))
        assertNull(idle.begin("  "))
    }

    @Test
    fun onlyOwnerCanCompleteRequest() {
        val opening = checkNotNull(RecordsAttachmentOpenStateHolder().begin("file-1"))

        assertSame(opening, opening.complete(2))
        assertEquals(
            RecordsAttachmentOpenStateHolder(requestId = 1),
            opening.complete(1),
        )
    }

    @Test
    fun invalidationAdvancesIdentityOnlyWhileOpening() {
        val idle = RecordsAttachmentOpenStateHolder(requestId = 7)
        val opening = checkNotNull(idle.begin("file-1"))

        val invalidated = opening.invalidate()

        assertEquals(9L, invalidated.requestId)
        assertNull(invalidated.path)
        assertFalse(invalidated.owns(8))
        assertSame(invalidated, invalidated.invalidate())
    }
}
