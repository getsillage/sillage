package app.sillage.ui.records

import app.sillage.core.domain.records.Memo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SillageRecordQuickActionsSheetTest {
    @Test
    fun presentationUsesIdleActionsAndBlankFallback() {
        val presentation = sillageRecordQuickActionsPresentation(
            memo = memo(content = "   "),
            confirmingDelete = false,
            strings = strings(),
        )

        assertEquals("Blank record", presentation.contentExcerpt)
        assertFalse(presentation.favoriteActive)
        assertEquals("Favorite", presentation.favoriteTitle)
        assertEquals("Keep this close", presentation.favoriteSupporting)
        assertEquals("Archive", presentation.archiveTitle)
        assertEquals("Move out of records", presentation.archiveSupporting)
        assertEquals("Delete", presentation.deleteTitle)
        assertEquals("Move to recently deleted", presentation.deleteSupporting)
    }

    @Test
    fun presentationUsesRecordDestinationWhenUnfavoritingActiveRecord() {
        val presentation = sillageRecordQuickActionsPresentation(
            memo = memo(favoritedAt = "2026-07-31T01:00:00Z"),
            confirmingDelete = false,
            strings = strings(),
        )

        assertTrue(presentation.favoriteActive)
        assertEquals("Unfavorite", presentation.favoriteTitle)
        assertEquals("Keep in records", presentation.favoriteSupporting)
    }

    @Test
    fun presentationUsesArchivedAndConfirmDeleteActions() {
        val presentation = sillageRecordQuickActionsPresentation(
            memo = memo(
                favoritedAt = "2026-07-31T01:00:00Z",
                archivedAt = "2026-07-31T02:00:00Z",
            ),
            confirmingDelete = true,
            strings = strings(),
        )

        assertEquals("Keep in archive", presentation.favoriteSupporting)
        assertEquals("Unarchive", presentation.archiveTitle)
        assertEquals("Return to records", presentation.archiveSupporting)
        assertEquals("Confirm delete", presentation.deleteTitle)
        assertEquals("Tap again to delete", presentation.deleteSupporting)
    }

    private fun strings(): SillageRecordQuickActionsStrings = SillageRecordQuickActionsStrings(
        blankRecord = "Blank record",
        recordDescription = "Actions for July 31, 2026",
        editAction = "Edit",
        editSupporting = "Change this record",
        duplicateAction = "Duplicate",
        duplicateSupporting = "Copy into a draft",
        favoriteAction = "Favorite",
        unfavoriteAction = "Unfavorite",
        favoriteSupporting = "Keep this close",
        unfavoriteToRecordsSupporting = "Keep in records",
        unfavoriteToArchiveSupporting = "Keep in archive",
        archiveAction = "Archive",
        unarchiveAction = "Unarchive",
        archiveSupporting = "Move out of records",
        unarchiveSupporting = "Return to records",
        deleteAction = "Delete",
        confirmDeleteAction = "Confirm delete",
        deleteSupporting = "Move to recently deleted",
        confirmDeleteSupporting = "Tap again to delete",
    )

    private fun memo(
        content: String = "Quick actions",
        favoritedAt: String? = null,
        archivedAt: String? = null,
    ): Memo = Memo(
        id = "quick-actions-record",
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
