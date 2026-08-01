package app.sillage.features.records

enum class RecordsEditorBusyReason {
    AttachmentUpload,
    Operation,
}

data class RecordsEditorActionContext(
    val destinationAvailable: Boolean,
    val hostOperationInProgress: Boolean,
)

fun RecordsFeatureStateHolder.hasUnsavedEditorDraft(
    context: RecordsEditorActionContext,
): Boolean {
    return context.destinationAvailable && editor.dirty
}

fun RecordsFeatureStateHolder.editorBusyReason(
    context: RecordsEditorActionContext,
): RecordsEditorBusyReason? {
    if (!context.destinationAvailable) {
        return null
    }
    return when {
        editor.uploadingAttachment -> RecordsEditorBusyReason.AttachmentUpload
        context.hostOperationInProgress ||
            selection.selectedMemo?.id?.let(mutation::isActive) == true -> {
            RecordsEditorBusyReason.Operation
        }
        else -> null
    }
}

fun RecordsFeatureStateHolder.canRunEditorAction(
    context: RecordsEditorActionContext,
): Boolean {
    return context.destinationAvailable && editorBusyReason(context) == null
}
