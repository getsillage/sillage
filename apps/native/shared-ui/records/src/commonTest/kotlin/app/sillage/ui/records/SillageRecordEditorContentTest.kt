package app.sillage.ui.records

import app.sillage.core.domain.records.Memo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SillageRecordEditorContentTest {
    @Test
    fun newOfflineRecordHidesAttachmentActionAndSummary() {
        val presentation = presentation(
            memo = null,
            showAttachmentAction = false,
        )

        assertFalse(presentation.showAttachmentAction)
        assertFalse(presentation.showSummary)
        assertEquals("Add attachment", presentation.attachmentAction)
    }

    @Test
    fun existingOnlineRecordShowsAttachmentActionAndSummary() {
        val presentation = presentation(
            memo = memo(),
            showAttachmentAction = true,
        )

        assertTrue(presentation.showAttachmentAction)
        assertTrue(presentation.showSummary)
    }

    @Test
    fun attachmentUploadUsesBusyActionCopy() {
        val presentation = presentation(
            memo = memo(),
            showAttachmentAction = true,
            uploadingAttachment = true,
        )

        assertEquals("Uploading…", presentation.attachmentAction)
    }

    private fun presentation(
        memo: Memo?,
        showAttachmentAction: Boolean,
        uploadingAttachment: Boolean = false,
    ): SillageRecordEditorContentPresentation = sillageRecordEditorContentPresentation(
        memo = memo,
        showAttachmentAction = showAttachmentAction,
        uploadingAttachment = uploadingAttachment,
        strings = strings,
    )

    private val strings = SillageRecordEditorContentStrings(
        entryDateLabel = "Date",
        entryDatePlaceholder = "YYYY-MM-DD",
        pickDateContentDescription = "Pick date",
        favoritedStatus = "Favorited",
        archivedStatus = "Archived",
        addAttachmentAction = "Add attachment",
        uploadingAttachmentAction = "Uploading…",
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
