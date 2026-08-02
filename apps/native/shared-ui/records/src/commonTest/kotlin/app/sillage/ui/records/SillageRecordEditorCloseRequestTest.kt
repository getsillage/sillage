package app.sillage.ui.records

import app.sillage.features.records.RecordsEditorActionContext
import app.sillage.features.records.RecordsEditorStateHolder
import app.sillage.features.records.RecordsFeatureStateHolder
import kotlin.test.Test
import kotlin.test.assertEquals

class SillageRecordEditorCloseRequestTest {
    @Test
    fun cleanEditorClosesImmediately() {
        assertEquals(
            SillageRecordEditorCloseRequest.Close,
            sillageRecordEditorCloseRequest(
                state = RecordsFeatureStateHolder(),
                context = context(),
            ),
        )
    }

    @Test
    fun dirtyEditorRequestsDiscardConfirmation() {
        assertEquals(
            SillageRecordEditorCloseRequest.ConfirmDiscard,
            sillageRecordEditorCloseRequest(
                state = RecordsFeatureStateHolder(
                    editor = RecordsEditorStateHolder(
                        draftContent = "Changed",
                        initialDraftContent = "Initial",
                    ),
                ),
                context = context(),
            ),
        )
    }

    @Test
    fun unavailableEditorDestinationDoesNotRequestDiscardConfirmation() {
        assertEquals(
            SillageRecordEditorCloseRequest.Close,
            sillageRecordEditorCloseRequest(
                state = RecordsFeatureStateHolder(
                    editor = RecordsEditorStateHolder(
                        draftContent = "Changed",
                        initialDraftContent = "Initial",
                    ),
                ),
                context = context(destinationAvailable = false),
            ),
        )
    }

    private fun context(
        destinationAvailable: Boolean = true,
    ): RecordsEditorActionContext = RecordsEditorActionContext(
        destinationAvailable = destinationAvailable,
        hostOperationInProgress = false,
    )
}
