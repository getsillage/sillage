package app.sillage.ui.records

import app.sillage.core.domain.records.Memo
import app.sillage.features.records.RecordsEditorActionContext
import app.sillage.features.records.RecordsEditorStateHolder
import app.sillage.features.records.RecordsFeatureStateHolder
import app.sillage.features.records.RecordsSelectionStateHolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SillageRecordEditorScreenChromeTest {
    @Test
    fun newEditorUsesNewTitleAndEnablesBack() {
        val presentation = presentation(RecordsFeatureStateHolder())

        assertEquals("New record", presentation.title)
        assertTrue(presentation.backEnabled)
    }

    @Test
    fun selectedRecordUsesEditTitle() {
        val presentation = presentation(
            RecordsFeatureStateHolder(
                selection = RecordsSelectionStateHolder(selectedMemo = memo()),
            ),
        )

        assertEquals("Edit record", presentation.title)
    }

    @Test
    fun attachmentUploadDisablesBack() {
        val presentation = presentation(
            RecordsFeatureStateHolder(
                editor = RecordsEditorStateHolder(uploadingAttachment = true),
            ),
        )

        assertFalse(presentation.backEnabled)
    }

    private fun presentation(
        state: RecordsFeatureStateHolder,
    ): SillageRecordEditorScreenChromePresentation =
        sillageRecordEditorScreenChromePresentation(
            state = state,
            context = RecordsEditorActionContext(
                destinationAvailable = true,
                hostOperationInProgress = false,
            ),
            strings = SillageRecordEditorScreenChromeStrings(
                newRecordTitle = "New record",
                editRecordTitle = "Edit record",
                backContentDescription = "Back",
            ),
        )

    private fun memo(): Memo = Memo(
        id = "memo",
        content = "Record",
        entryDate = "2026-08-02",
        version = 1,
        createdAt = "2026-08-02T00:00:00Z",
        updatedAt = "2026-08-02T00:00:00Z",
        favoritedAt = null,
        archivedAt = null,
        deletedAt = null,
    )
}
