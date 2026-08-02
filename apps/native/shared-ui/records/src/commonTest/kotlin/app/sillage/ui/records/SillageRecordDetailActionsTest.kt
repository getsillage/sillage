package app.sillage.ui.records

import app.sillage.core.domain.records.Memo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SillageRecordDetailActionsTest {
    @Test
    fun missingRecordDisablesActions() {
        val presentation = presentation(memo = null)

        assertFalse(presentation.actionsEnabled)
        assertEquals("Favorite", presentation.favoriteAction)
        assertEquals("Archive", presentation.archiveAction)
    }

    @Test
    fun lifecycleStateSelectsInverseActions() {
        val presentation = presentation(
            memo = memo(
                favoritedAt = "2026-08-02T01:00:00Z",
                archivedAt = "2026-08-02T02:00:00Z",
            ),
        )

        assertTrue(presentation.actionsEnabled)
        assertTrue(presentation.favorited)
        assertEquals("Unfavorite", presentation.favoriteAction)
        assertEquals("Unarchive", presentation.archiveAction)
    }

    @Test
    fun hostOrMutationBusyStateDisablesActions() {
        assertFalse(presentation(memo(), operationBlocked = true).actionsEnabled)
        assertFalse(presentation(memo(), mutating = true).actionsEnabled)
    }

    private fun presentation(
        memo: Memo?,
        operationBlocked: Boolean = false,
        mutating: Boolean = false,
    ): SillageRecordDetailActionPresentation = sillageRecordDetailActionPresentation(
        memo = memo,
        operationBlocked = operationBlocked,
        mutating = mutating,
        strings = strings(),
    )

    private fun strings(): SillageRecordDetailActionStrings = SillageRecordDetailActionStrings(
        editContentDescription = "Edit",
        moreContentDescription = "More",
        favoriteAction = "Favorite",
        unfavoriteAction = "Unfavorite",
        archiveAction = "Archive",
        unarchiveAction = "Unarchive",
        deleteAction = "Delete",
        deleteTitle = "Delete record?",
        deleteSupporting = "This cannot be undone.",
        confirmDeleteAction = "Delete",
        cancelAction = "Cancel",
    )

    private fun memo(
        favoritedAt: String? = null,
        archivedAt: String? = null,
    ): Memo = Memo(
        id = "memo",
        content = "Record",
        entryDate = "2026-08-02",
        version = 1,
        createdAt = "2026-08-02T00:00:00Z",
        updatedAt = "2026-08-02T00:00:00Z",
        favoritedAt = favoritedAt,
        archivedAt = archivedAt,
        deletedAt = null,
    )
}
