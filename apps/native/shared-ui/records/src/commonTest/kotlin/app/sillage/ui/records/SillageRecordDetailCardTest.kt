package app.sillage.ui.records

import app.sillage.core.domain.records.Memo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SillageRecordDetailCardTest {
    @Test
    fun activeRecordHasNoStatusLine() {
        val presentation = sillageRecordDetailPresentation(memo(), strings())

        assertNull(presentation.statusLine)
        assertFalse(presentation.showBlankRecord)
    }

    @Test
    fun lifecycleStatusesFollowRecordState() {
        val presentation = sillageRecordDetailPresentation(
            memo(
                favoritedAt = "2026-08-02T01:00:00Z",
                archivedAt = "2026-08-02T02:00:00Z",
            ),
            strings(),
        )

        assertEquals("Favorited · Archived", presentation.statusLine)
    }

    @Test
    fun whitespaceOnlyContentUsesBlankFallback() {
        val presentation = sillageRecordDetailPresentation(
            memo(content = "  \n "),
            strings(),
        )

        assertTrue(presentation.showBlankRecord)
    }

    private fun strings(): SillageRecordDetailStrings = SillageRecordDetailStrings(
        entryDateLabel = "August 2, 2026",
        blankRecord = "Blank record",
        favoritedStatus = "Favorited",
        archivedStatus = "Archived",
    )

    private fun memo(
        content: String = "Record",
        favoritedAt: String? = null,
        archivedAt: String? = null,
    ): Memo = Memo(
        id = "memo",
        content = content,
        entryDate = "2026-08-02",
        version = 1,
        createdAt = "2026-08-02T00:00:00Z",
        updatedAt = "2026-08-02T00:00:00Z",
        favoritedAt = favoritedAt,
        archivedAt = archivedAt,
        deletedAt = null,
    )
}
