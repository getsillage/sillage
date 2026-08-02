package app.sillage.ui.records

import app.sillage.core.domain.records.Memo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SillageRecordRowTest {
    @Test
    fun presentationUsesBlankFallbackAndHostDateLabel() {
        val presentation = sillageRecordRowPresentation(
            memo = memo(content = "   "),
            mutating = false,
            strings = strings(),
        )

        assertEquals("Blank record", presentation.content)
        assertEquals("July 31, 2026", presentation.entryDateLabel)
        assertNull(presentation.statusLabel)
        assertFalse(presentation.mutating)
    }

    @Test
    fun presentationJoinsActiveStatusesAndExposesMutation() {
        val presentation = sillageRecordRowPresentation(
            memo = memo(
                content = "Shared row",
                favoritedAt = "2026-07-31T01:00:00Z",
                archivedAt = "2026-07-31T02:00:00Z",
            ),
            mutating = true,
            strings = strings(),
        )

        assertEquals("Shared row", presentation.content)
        assertEquals("Favorited · Archived", presentation.statusLabel)
        assertTrue(presentation.mutating)
    }

    private fun strings(): SillageRecordRowStrings = SillageRecordRowStrings(
        blankRecord = "Blank record",
        entryDateLabel = "July 31, 2026",
        moreActionsLabel = "More actions",
        savingDescription = "Saving",
        favoritedStatus = "Favorited",
        archivedStatus = "Archived",
    )

    private fun memo(
        content: String,
        favoritedAt: String? = null,
        archivedAt: String? = null,
    ): Memo = Memo(
        id = "record-row",
        content = content,
        entryDate = "2026-07-31",
        version = 2,
        createdAt = "2026-07-31T00:00:00Z",
        updatedAt = "2026-07-31T01:00:00Z",
        favoritedAt = favoritedAt,
        archivedAt = archivedAt,
        deletedAt = null,
    )
}
