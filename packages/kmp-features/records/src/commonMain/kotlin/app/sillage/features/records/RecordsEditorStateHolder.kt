package app.sillage.features.records

/** Immutable record-editor draft and attachment-upload state. */
data class RecordsEditorStateHolder(
    val sessionId: Long = 0,
    val draftContent: String = "",
    val draftEntryDate: String = "",
    val initialDraftContent: String = "",
    val initialDraftEntryDate: String = "",
    val markdownPreview: Boolean = false,
    val uploadingAttachment: Boolean = false,
) {
    val dirty: Boolean
        get() = draftContent != initialDraftContent || draftEntryDate != initialDraftEntryDate

    fun open(
        draftContent: String,
        draftEntryDate: String,
        initialDraftContent: String,
        initialDraftEntryDate: String,
    ): RecordsEditorStateHolder {
        return copy(
            sessionId = sessionId + 1,
            draftContent = draftContent,
            draftEntryDate = draftEntryDate,
            initialDraftContent = initialDraftContent,
            initialDraftEntryDate = initialDraftEntryDate,
            markdownPreview = false,
            uploadingAttachment = false,
        )
    }

    fun updateContent(value: String): RecordsEditorStateHolder = copy(draftContent = value)

    fun updateEntryDate(value: String): RecordsEditorStateHolder = copy(draftEntryDate = value)

    fun setMarkdownPreview(value: Boolean): RecordsEditorStateHolder = copy(markdownPreview = value)

    fun appendFormattedSnippet(snippet: String): RecordsEditorStateHolder {
        val separator = if (draftContent.isBlank() || snippet.startsWith("\n")) "" else " "
        return copy(
            draftContent = draftContent + separator + snippet,
            markdownPreview = false,
        )
    }

    fun appendAttachmentSnippet(snippet: String): RecordsEditorStateHolder {
        return copy(draftContent = draftContent + snippet)
    }

    fun beginAttachmentUpload(expectedSessionId: Long): RecordsEditorStateHolder? {
        if (sessionId != expectedSessionId || uploadingAttachment) return null
        return copy(uploadingAttachment = true)
    }

    fun canApplyAttachmentUpload(expectedSessionId: Long): Boolean {
        return sessionId == expectedSessionId && uploadingAttachment
    }

    fun finishAttachmentUpload(expectedSessionId: Long): RecordsEditorStateHolder {
        return if (canApplyAttachmentUpload(expectedSessionId)) {
            copy(uploadingAttachment = false)
        } else {
            this
        }
    }

    fun stopAttachmentUpload(): RecordsEditorStateHolder = copy(uploadingAttachment = false)

    fun reset(entryDate: String): RecordsEditorStateHolder {
        return copy(
            draftContent = "",
            draftEntryDate = entryDate,
            initialDraftContent = "",
            initialDraftEntryDate = entryDate,
            markdownPreview = false,
            uploadingAttachment = false,
        )
    }
}
