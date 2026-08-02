package app.sillage.ui.records

import kotlin.test.Test
import kotlin.test.assertEquals

class SillageRecordEditorCloseRequestTest {
    @Test
    fun cleanEditorClosesImmediately() {
        assertEquals(
            SillageRecordEditorCloseRequest.Close,
            sillageRecordEditorCloseRequest(hasUnsavedChanges = false),
        )
    }

    @Test
    fun dirtyEditorRequestsDiscardConfirmation() {
        assertEquals(
            SillageRecordEditorCloseRequest.ConfirmDiscard,
            sillageRecordEditorCloseRequest(hasUnsavedChanges = true),
        )
    }
}
