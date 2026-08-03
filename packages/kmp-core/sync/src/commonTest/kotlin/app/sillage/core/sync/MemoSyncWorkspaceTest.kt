package app.sillage.core.sync

import app.sillage.core.domain.records.Memo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MemoSyncWorkspaceTest {
    @Test
    fun pendingResolutionReusesMatchingMutationAndDropsUnsyncedDeletion() {
        val active = memo(id = "active", version = 2)
        val deleted = memo(id = "deleted", deletedAt = "2026-08-03T01:00:00Z")
        val marker = PendingMemoMutation(
            mutationId = "mutation-stable",
            memoVersion = active.version,
            memoUpdatedAt = active.updatedAt,
            action = MEMO_SYNC_ACTION_UPDATE,
        )
        var generated = 0

        val resolved = resolvePendingMemoSyncs(
            memos = listOf(active, deleted),
            cloudVersions = mapOf(active.id to 1L),
            pendingMutations = mapOf(active.id to marker, deleted.id to marker.copy(mutationId = "drop")),
            newMutationId = { "generated-${++generated}" },
        )

        assertEquals(1, resolved.pending.size)
        assertEquals("mutation-stable", resolved.pending.single().mutationId)
        assertEquals(1L, resolved.pending.single().baseVersion)
        assertEquals(MEMO_SYNC_ACTION_UPDATE, resolved.pending.single().action)
        assertFalse(deleted.id in resolved.pendingMutations)
        assertEquals(0, generated)
    }

    @Test
    fun restoreApplyRetainsNewerLocalFieldsAndSchedulesUpdate() {
        val local = memo(
            id = "memo-1",
            content = "edited after restore",
            entryDate = "2026-08-04",
            version = 4,
            updatedAt = "2026-08-03T04:00:00Z",
        )
        val server = memo(
            id = local.id,
            content = "server original",
            version = 3,
            updatedAt = "2026-08-03T03:00:00Z",
        )
        val restore = PendingMemoMutation(
            mutationId = "restore-1",
            memoVersion = local.version,
            memoUpdatedAt = local.updatedAt,
            action = MEMO_SYNC_ACTION_RESTORE,
        )

        val merged = mergeAppliedMemoSyncs(
            localMemos = listOf(local),
            cloudVersions = mapOf(local.id to 2L),
            pendingMutations = mapOf(local.id to restore),
            appliedMemos = listOf(AppliedMemoSync(restore.mutationId, server)),
            newMutationId = { "update-2" },
            currentTimestamp = { "2026-08-03T05:00:00Z" },
        )

        assertEquals(local, merged.memos.single())
        assertEquals(server.version, merged.cloudVersions[local.id])
        val followUp = merged.pendingMutations.getValue(local.id)
        assertEquals("update-2", followUp.mutationId)
        assertEquals(MEMO_SYNC_ACTION_UPDATE, followUp.action)
    }

    @Test
    fun ordinaryAppliedMutationAdoptsCanonicalServerMemo() {
        val local = memo(id = "memo-1", version = 2)
        val server = local.copy(version = 3, updatedAt = "2026-08-03T03:00:00Z")
        val marker = PendingMemoMutation(
            mutationId = "update-1",
            memoVersion = local.version,
            memoUpdatedAt = local.updatedAt,
            action = MEMO_SYNC_ACTION_UPDATE,
        )

        val merged = mergeAppliedMemoSyncs(
            localMemos = listOf(local),
            cloudVersions = mapOf(local.id to 1L),
            pendingMutations = mapOf(local.id to marker),
            appliedMemos = listOf(AppliedMemoSync(marker.mutationId, server)),
            newMutationId = { error("must not generate") },
            currentTimestamp = { error("must not read time") },
        )

        assertEquals(server, merged.memos.single())
        assertEquals(server.version, merged.cloudVersions[local.id])
        assertTrue(merged.pendingMutations.isEmpty())
    }

    @Test
    fun emptyCloudBaselineProducesCreateWithoutBaseVersion() {
        val local = memo(id = "memo-1")
        val resolved = resolvePendingMemoSyncs(
            memos = listOf(local),
            cloudVersions = emptyMap(),
            pendingMutations = emptyMap(),
            newMutationId = { "create-1" },
        )

        val pending = resolved.pending.single()
        assertEquals(MEMO_SYNC_ACTION_CREATE, pending.action)
        assertNull(pending.baseVersion)
    }

    private fun memo(
        id: String,
        content: String = "content",
        entryDate: String = "2026-08-03",
        version: Long = 1,
        updatedAt: String = "2026-08-03T01:00:00Z",
        deletedAt: String? = null,
    ) = Memo(
        id = id,
        content = content,
        entryDate = entryDate,
        version = version,
        createdAt = "2026-08-03T00:00:00Z",
        updatedAt = updatedAt,
        favoritedAt = null,
        archivedAt = null,
        deletedAt = deletedAt,
        purgedAt = null,
    )
}
