package app.sillage.features.sync

import app.sillage.core.domain.records.Memo
import app.sillage.core.sync.ConflictMemoSync
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MemoSyncConflictStateHolderTest {
    @Test
    fun findsReplacesAndRemovesByResourceIdentity() {
        val first = item("memo-1")
        val second = item("memo-2")
        val state = MemoSyncConflictStateHolder(listOf(first, second))

        assertEquals(first, state.find("memo-1"))
        assertEquals(listOf(second), state.remove("memo-1").items)
        assertNull(state.find("missing"))
        assertEquals(listOf(second), state.replace(listOf(second)).items)
    }

    private fun item(resourceId: String) = MemoSyncConflictItem(
        conflict = ConflictMemoSync(
            mutationId = "mutation-$resourceId",
            resourceId = resourceId,
            clientVersion = 1,
            serverVersion = 2,
            serverMemo = memo(resourceId).copy(version = 2),
        ),
        localMemo = memo(resourceId),
    )

    private fun memo(id: String) = Memo(
        id = id,
        content = "content",
        entryDate = "2026-08-01",
        version = 1,
        createdAt = "2026-08-01T00:00:00Z",
        updatedAt = "2026-08-01T00:00:00Z",
        favoritedAt = null,
        archivedAt = null,
        deletedAt = null,
        purgedAt = null,
    )
}
