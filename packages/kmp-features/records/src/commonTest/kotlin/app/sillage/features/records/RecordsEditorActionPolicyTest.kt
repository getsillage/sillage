package app.sillage.features.records

import app.sillage.core.domain.records.Memo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecordsEditorActionPolicyTest {
    @Test
    fun draftAndActionsRequireEditorDestination() {
        val state = RecordsFeatureStateHolder(
            editor = RecordsEditorStateHolder(
                draftContent = "changed",
                initialDraftContent = "initial",
            ),
        )
        val unavailable = context(destinationAvailable = false)

        assertFalse(state.hasUnsavedEditorDraft(unavailable))
        assertNull(state.editorBusyReason(unavailable))
        assertFalse(state.canRunEditorAction(unavailable))
    }

    @Test
    fun attachmentUploadHasPriorityOverOtherOperations() {
        val selected = memo()
        val state = RecordsFeatureStateHolder(
            selection = RecordsSelectionStateHolder(selectedMemo = selected),
            mutation = RecordsMutationStateHolder(activeMemoIds = setOf(selected.id)),
            editor = RecordsEditorStateHolder(uploadingAttachment = true),
        )

        assertEquals(
            RecordsEditorBusyReason.AttachmentUpload,
            state.editorBusyReason(context(hostOperationInProgress = true)),
        )
        assertFalse(state.canRunEditorAction(context()))
    }

    @Test
    fun hostAndSelectedMemoOperationsBlockEditorActions() {
        val selected = memo()
        val selectedMutation = RecordsFeatureStateHolder(
            selection = RecordsSelectionStateHolder(selectedMemo = selected),
            mutation = RecordsMutationStateHolder(activeMemoIds = setOf(selected.id)),
        )
        val idle = RecordsFeatureStateHolder()

        assertEquals(
            RecordsEditorBusyReason.Operation,
            idle.editorBusyReason(context(hostOperationInProgress = true)),
        )
        assertEquals(
            RecordsEditorBusyReason.Operation,
            selectedMutation.editorBusyReason(context()),
        )
        assertNull(idle.editorBusyReason(context()))
        assertTrue(idle.canRunEditorAction(context()))
    }

    @Test
    fun dirtyEditorDraftIsReportedWhenDestinationIsActive() {
        val state = RecordsFeatureStateHolder(
            editor = RecordsEditorStateHolder(
                draftEntryDate = "2026-08-02",
                initialDraftEntryDate = "2026-08-01",
            ),
        )

        assertTrue(state.hasUnsavedEditorDraft(context()))
    }

    private fun context(
        destinationAvailable: Boolean = true,
        hostOperationInProgress: Boolean = false,
    ): RecordsEditorActionContext {
        return RecordsEditorActionContext(
            destinationAvailable = destinationAvailable,
            hostOperationInProgress = hostOperationInProgress,
        )
    }

    private fun memo(): Memo {
        return Memo(
            id = "memo-1",
            content = "record",
            entryDate = "2026-08-02",
            version = 1,
            createdAt = "2026-08-02T00:00:00Z",
            updatedAt = "2026-08-02T00:00:00Z",
            favoritedAt = null,
            archivedAt = null,
            deletedAt = null,
        )
    }
}
