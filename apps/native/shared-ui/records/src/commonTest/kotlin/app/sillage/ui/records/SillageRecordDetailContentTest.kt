package app.sillage.ui.records

import app.sillage.core.domain.records.Memo
import app.sillage.features.records.RecordsFeatureStateHolder
import app.sillage.features.records.RecordsSelectionStateHolder
import kotlin.test.Test
import kotlin.test.assertEquals

class SillageRecordDetailContentTest {
    @Test
    fun missingRecordSelectsMissingContent() {
        assertEquals(
            SillageRecordDetailBody.Missing,
            sillageRecordDetailBody(RecordsFeatureStateHolder()),
        )
    }

    @Test
    fun selectedRecordSelectsDetailSections() {
        assertEquals(
            SillageRecordDetailBody.Content,
            sillageRecordDetailBody(
                RecordsFeatureStateHolder(
                    selection = RecordsSelectionStateHolder(selectedMemo = memo()),
                ),
            ),
        )
    }

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
