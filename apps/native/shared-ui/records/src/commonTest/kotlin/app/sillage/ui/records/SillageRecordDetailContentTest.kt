package app.sillage.ui.records

import app.sillage.core.domain.records.Memo
import kotlin.test.Test
import kotlin.test.assertEquals

class SillageRecordDetailContentTest {
    @Test
    fun missingRecordSelectsMissingContent() {
        assertEquals(SillageRecordDetailBody.Missing, sillageRecordDetailBody(null))
    }

    @Test
    fun selectedRecordSelectsDetailSections() {
        assertEquals(SillageRecordDetailBody.Content, sillageRecordDetailBody(memo()))
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
