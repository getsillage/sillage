package app.sillage.features.sync

import app.sillage.core.domain.records.Memo
import app.sillage.core.sync.ConflictMemoSync
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class SyncFeatureStateHolderTest {
    @Test
    fun applyPushConflictsReplacesOnlyWhenNonEmpty() {
        val existing = item("a")
        val state = SyncFeatureStateHolder(
            conflicts = MemoSyncConflictStateHolder(items = listOf(existing)),
        )

        val unchanged = state.applyPushConflicts(emptyList())
        val replaced = state.applyPushConflicts(listOf(item("b"), item("c")))

        assertSame(state, unchanged)
        assertEquals(listOf("b", "c"), replaced.items.map { it.conflict.resourceId })
    }

    @Test
    fun removeConflictDropsMatchingItem() {
        val state = SyncFeatureStateHolder(
            conflicts = MemoSyncConflictStateHolder(items = listOf(item("a"), item("b"))),
        )

        val removed = state.removeConflict("a")

        assertEquals(listOf("b"), removed.items.map { it.conflict.resourceId })
        assertNull(removed.findConflict("a"))
        assertEquals("b", removed.findConflict("b")?.conflict?.resourceId)
    }

    private fun item(id: String): MemoSyncConflictItem {
        val server = Memo(
            id = id,
            content = "server-$id",
            entryDate = "2026-08-01",
            version = 2,
            createdAt = "2026-08-01T00:00:00Z",
            updatedAt = "2026-08-01T01:00:00Z",
            favoritedAt = null,
            archivedAt = null,
            deletedAt = null,
        )
        return MemoSyncConflictItem(
            conflict = ConflictMemoSync(
                mutationId = "mut-$id",
                resourceId = id,
                clientVersion = 1,
                serverVersion = 2,
                serverMemo = server,
            ),
            localMemo = server.copy(content = "local-$id", version = 1),
        )
    }
}
