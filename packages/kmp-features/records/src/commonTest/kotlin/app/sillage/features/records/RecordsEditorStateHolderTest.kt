package app.sillage.features.records

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecordsEditorStateHolderTest {
    @Test
    fun openingDraftAdvancesSessionAndCapturesInitialSnapshot() {
        val opened = RecordsEditorStateHolder(sessionId = 4, uploadingAttachment = true).open(
            draftContent = "draft",
            draftEntryDate = "2026-08-02",
            initialDraftContent = "original",
            initialDraftEntryDate = "2026-08-01",
        )

        assertEquals(5, opened.sessionId)
        assertEquals("draft", opened.draftContent)
        assertEquals("original", opened.initialDraftContent)
        assertTrue(opened.dirty)
        assertFalse(opened.markdownPreview)
        assertFalse(opened.uploadingAttachment)
    }

    @Test
    fun contentDateAndPreviewTransitionsAreDeterministic() {
        val initial = RecordsEditorStateHolder().open("content", "2026-08-01", "content", "2026-08-01")

        assertFalse(initial.dirty)
        assertTrue(initial.updateContent("changed").dirty)
        assertTrue(initial.updateEntryDate("2026-08-02").dirty)
        assertTrue(initial.setMarkdownPreview(true).markdownPreview)
        assertEquals("content **bold**", initial.appendFormattedSnippet("**bold**").draftContent)
        assertEquals("content\n- item\n", initial.appendFormattedSnippet("\n- item\n").draftContent)
        assertEquals("content\n[file](url)", initial.appendAttachmentSnippet("\n[file](url)").draftContent)
    }

    @Test
    fun attachmentUploadIsBoundToEditorSession() {
        val idle = RecordsEditorStateHolder(sessionId = 7)

        assertNull(idle.beginAttachmentUpload(6))
        val uploading = requireNotNull(idle.beginAttachmentUpload(7))
        assertTrue(uploading.canApplyAttachmentUpload(7))
        assertFalse(uploading.canApplyAttachmentUpload(8))
        assertNull(uploading.beginAttachmentUpload(7))
        assertFalse(uploading.finishAttachmentUpload(7).uploadingAttachment)
        assertEquals(uploading, uploading.finishAttachmentUpload(8))
    }

    @Test
    fun resetClearsTransientDraftStateWithoutReusingSession() {
        val reset = RecordsEditorStateHolder(
            sessionId = 3,
            draftContent = "changed",
            draftEntryDate = "2026-08-02",
            initialDraftContent = "original",
            initialDraftEntryDate = "2026-08-01",
            markdownPreview = true,
            uploadingAttachment = true,
        ).reset("2026-08-03")

        assertEquals(3, reset.sessionId)
        assertEquals("", reset.draftContent)
        assertEquals("2026-08-03", reset.draftEntryDate)
        assertFalse(reset.dirty)
        assertFalse(reset.markdownPreview)
        assertFalse(reset.uploadingAttachment)
    }
}
