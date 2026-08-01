package app.sillage.ui.records

import app.sillage.core.domain.records.Memo
import app.sillage.features.records.RecordsFeatureStateHolder
import app.sillage.features.records.RecordsMutationStateHolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SillageRecentlyDeletedRecordRowTest {
    @Test
    fun presentationUsesSharedMutationOwnershipAndBlankFallback() {
        val memo = deletedMemo(content = "   ")
        val strings = strings()

        val idle = sillageRecentlyDeletedRecordPresentation(
            state = RecordsFeatureStateHolder(),
            memo = memo,
            strings = strings,
        )
        val mutating = sillageRecentlyDeletedRecordPresentation(
            state = RecordsFeatureStateHolder(
                mutation = RecordsMutationStateHolder(activeMemoIds = setOf(memo.id)),
            ),
            memo = memo,
            strings = strings,
        )

        assertEquals("Blank record", idle.contentExcerpt)
        assertEquals("Deleted yesterday", idle.deletedAtLabel)
        assertFalse(idle.mutating)
        assertTrue(mutating.mutating)
    }

    private fun strings(): SillageRecentlyDeletedRecordStrings =
        SillageRecentlyDeletedRecordStrings(
            blankRecord = "Blank record",
            deletedAtLabel = "Deleted yesterday",
            purgeSupporting = "This cannot be undone",
            restoreAction = "Restore",
            deleteForeverAction = "Delete forever",
            confirmDeleteAction = "Confirm delete",
            cancelAction = "Cancel",
        )

    private fun deletedMemo(content: String): Memo = Memo(
        id = "deleted-record",
        content = content,
        entryDate = "2026-07-31",
        version = 2,
        createdAt = "2026-07-31T00:00:00Z",
        updatedAt = "2026-07-31T01:00:00Z",
        favoritedAt = null,
        archivedAt = null,
        deletedAt = "2026-07-31T01:00:00Z",
    )
}
