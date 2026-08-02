package app.sillage.ui.records

import app.sillage.core.domain.records.Memo
import app.sillage.features.records.RecordsEditorActionContext
import app.sillage.features.records.RecordsEditorStateHolder
import app.sillage.features.records.RecordsFeatureStateHolder
import app.sillage.features.records.RecordsMutationStateHolder
import app.sillage.features.records.RecordsSelectionStateHolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SillageRecordEditorActionsTest {
    @Test
    fun idleSaveUsesSaveDescriptionWithoutProgress() {
        val presentation = presentation(memo = null)

        assertEquals("Save", presentation.saveContentDescription)
        assertFalse(presentation.showSaveProgress)
        assertTrue(presentation.actionsEnabled)
    }

    @Test
    fun attachmentUploadPreemptsSavingDescription() {
        val presentation = presentation(
            memo = memo(),
            saving = true,
            uploadingAttachment = true,
        )

        assertEquals("Uploading attachment", presentation.saveContentDescription)
        assertTrue(presentation.showSaveProgress)
    }

    @Test
    fun savingUsesProgressAndSavingDescription() {
        val presentation = presentation(memo(), saving = true)

        assertEquals("Saving", presentation.saveContentDescription)
        assertTrue(presentation.showSaveProgress)
    }

    @Test
    fun selectedMemoMutationUsesSavingPresentationAndDisablesActions() {
        val presentation = presentation(memo(), mutating = true)

        assertEquals("Saving", presentation.saveContentDescription)
        assertTrue(presentation.showSaveProgress)
        assertFalse(presentation.actionsEnabled)
    }

    @Test
    fun lifecycleStateSelectsInverseMenuActions() {
        val presentation = presentation(
            memo(
                favoritedAt = "2026-08-02T01:00:00Z",
                archivedAt = "2026-08-02T02:00:00Z",
            ),
        )

        assertTrue(presentation.favorited)
        assertEquals("Unfavorite", presentation.favoriteAction)
        assertEquals("Unarchive", presentation.archiveAction)
    }

    private fun presentation(
        memo: Memo?,
        saving: Boolean = false,
        uploadingAttachment: Boolean = false,
        mutating: Boolean = false,
    ): SillageRecordEditorActionPresentation = sillageRecordEditorActionPresentation(
        state = RecordsFeatureStateHolder(
            selection = RecordsSelectionStateHolder(selectedMemo = memo),
            editor = RecordsEditorStateHolder(uploadingAttachment = uploadingAttachment),
            mutation = RecordsMutationStateHolder(
                activeMemoIds = if (mutating && memo != null) setOf(memo.id) else emptySet(),
            ),
        ),
        context = RecordsEditorActionContext(
            destinationAvailable = true,
            hostOperationInProgress = saving,
        ),
        strings = strings(),
    )

    private fun strings(): SillageRecordEditorActionStrings = SillageRecordEditorActionStrings(
        saveContentDescription = "Save",
        savingContentDescription = "Saving",
        attachmentUploadingContentDescription = "Uploading attachment",
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
