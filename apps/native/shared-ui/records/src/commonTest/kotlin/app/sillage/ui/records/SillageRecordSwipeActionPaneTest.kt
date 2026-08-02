package app.sillage.ui.records

import app.sillage.core.domain.records.Memo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SillageRecordSwipeActionPaneTest {
    @Test
    fun presentationSelectsIdleLabelsAndFavoriteVisibility() {
        val presentation = sillageRecordSwipeActionPresentation(
            memo = memo(),
            revealedOffset = 24f,
            strings = strings(),
        )

        assertEquals("Favorite", presentation.favoriteLabel)
        assertEquals("Archive", presentation.archiveLabel)
        assertFalse(presentation.favoriteActive)
        assertTrue(presentation.favoriteVisible)
        assertFalse(presentation.archiveVisible)
    }

    @Test
    fun presentationSelectsActiveLabelsAndArchiveVisibility() {
        val presentation = sillageRecordSwipeActionPresentation(
            memo = memo(
                favoritedAt = "2026-07-31T01:00:00Z",
                archivedAt = "2026-07-31T02:00:00Z",
            ),
            revealedOffset = -24f,
            strings = strings(),
        )

        assertEquals("Unfavorite", presentation.favoriteLabel)
        assertEquals("Restore", presentation.archiveLabel)
        assertTrue(presentation.favoriteActive)
        assertFalse(presentation.favoriteVisible)
        assertTrue(presentation.archiveVisible)
    }

    private fun strings(): SillageRecordSwipeActionStrings = SillageRecordSwipeActionStrings(
        favoriteAction = "Favorite",
        unfavoriteAction = "Unfavorite",
        archiveAction = "Archive",
        restoreAction = "Restore",
    )

    private fun memo(
        favoritedAt: String? = null,
        archivedAt: String? = null,
    ): Memo = Memo(
        id = "swipe-record",
        content = "Swipe record",
        entryDate = "2026-07-31",
        version = 2,
        createdAt = "2026-07-31T00:00:00Z",
        updatedAt = "2026-07-31T01:00:00Z",
        favoritedAt = favoritedAt,
        archivedAt = archivedAt,
        deletedAt = null,
    )
}
