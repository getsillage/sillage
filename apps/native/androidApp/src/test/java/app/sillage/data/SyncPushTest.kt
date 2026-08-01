package app.sillage.data

import app.sillage.core.domain.records.Memo
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPushTest {
    @Test
    fun appliedResultKeepsCanonicalMemoAndReadsLegacyPinnedAt() {
        val resource = memoJson(
            content = "服务端规范内容",
            version = 1,
        ).put("pinnedAt", "2026-07-10T02:00:00Z")
        val results = JSONArray()
            .put(
                JSONObject()
                    .put("status", "applied")
                    .put("mutationId", "mutation-1")
                    .put("resourceId", "memo-1")
                    .put("resource", resource),
            )
            .put(
                JSONObject()
                    .put("status", "conflict")
                    .put("mutationId", "mutation-conflict")
                    .put("resourceId", "memo-2")
                    .put("clientVersion", 2)
                    .put("serverVersion", 3)
                    .put(
                        "serverResource",
                        memoJson(content = "服务端冲突版本", version = 3).put("id", "memo-2"),
                    ),
            )
            .put(JSONObject().put("status", "invalid"))

        val summary = syncPushSummaryFromResults(results)

        assertEquals(1, summary.applied)
        assertEquals(1, summary.conflict)
        assertEquals(1, summary.rejected)
        val applied = summary.appliedMemoSyncs.single()
        assertEquals("mutation-1", applied.mutationId)
        assertEquals(1L, applied.memo.version)
        assertEquals("服务端规范内容", applied.memo.content)
        assertEquals("2026-07-10T02:00:00Z", applied.memo.favoritedAt)
        val conflict = summary.conflictMemoSyncs.single()
        assertEquals("mutation-conflict", conflict.mutationId)
        assertEquals("memo-2", conflict.resourceId)
        assertEquals(2L, conflict.clientVersion)
        assertEquals(3L, conflict.serverVersion)
        assertEquals("服务端冲突版本", conflict.serverMemo?.content)
    }

    @Test
    fun keepLocalConflictAdoptsServerBaselineAndNewMutation() {
        val local = memo(version = 3, content = "本机待推送")
        val resolved = resolveConflictKeepLocalState(
            localMemos = listOf(local),
            cloudVersions = mapOf(local.id to 1L),
            pendingMutations = mapOf(
                local.id to PendingMemoMutation(
                    mutationId = "old-mutation",
                    memoVersion = local.version,
                    memoUpdatedAt = local.updatedAt,
                ),
            ),
            localMemo = local,
            serverVersion = 2L,
            newMutationId = { "new-mutation" },
        )
        // Local was already ahead of server; content and version stay the same.
        assertEquals(local, resolved.memos.single())
        assertEquals(2L, resolved.cloudVersions[local.id])
        assertEquals("new-mutation", resolved.pendingMutations[local.id]?.mutationId)

        val pending = resolvePendingMemoSyncs(
            memos = resolved.memos,
            cloudVersions = resolved.cloudVersions,
            pendingMutations = resolved.pendingMutations,
            newMutationId = { error("should reuse mutation") },
        )
        assertEquals(2L, pending.pending.single().baseVersion)
        assertEquals("new-mutation", pending.pending.single().mutationId)
    }

    @Test
    fun keepLocalConflictWhenServerVersionAtLeastLocalStillSchedulesResubmit() {
        // Realistic conflict: server advanced to the same or higher version than
        // the device's pending edit. Keep-local must bump the local memo so
        // resolvePendingMemoSyncs does not drop the pending push.
        val local = memo(
            version = 6,
            content = "本机冲突内容",
            updatedAt = "2026-07-30T01:00:00Z",
        )
        val resolvedEqual = resolveConflictKeepLocalState(
            localMemos = listOf(local),
            cloudVersions = mapOf(local.id to 5L),
            pendingMutations = mapOf(
                local.id to PendingMemoMutation(
                    mutationId = "old-mutation",
                    memoVersion = local.version,
                    memoUpdatedAt = local.updatedAt,
                ),
            ),
            localMemo = local,
            serverVersion = 6L,
            newMutationId = { "resubmit-equal" },
            now = "2026-07-30T02:00:00Z",
        )
        val keptEqual = resolvedEqual.memos.single()
        assertEquals(6L, resolvedEqual.cloudVersions[local.id])
        assertEquals(7L, keptEqual.version)
        assertEquals("本机冲突内容", keptEqual.content)
        assertEquals("2026-07-30T02:00:00Z", keptEqual.updatedAt)
        assertEquals("resubmit-equal", resolvedEqual.pendingMutations[local.id]?.mutationId)
        assertEquals(7L, resolvedEqual.pendingMutations[local.id]?.memoVersion)

        val pendingEqual = resolvePendingMemoSyncs(
            memos = resolvedEqual.memos,
            cloudVersions = resolvedEqual.cloudVersions,
            pendingMutations = resolvedEqual.pendingMutations,
            newMutationId = { error("should reuse mutation") },
        )
        assertEquals(1, pendingEqual.pending.size)
        assertEquals(6L, pendingEqual.pending.single().baseVersion)
        assertEquals("resubmit-equal", pendingEqual.pending.single().mutationId)
        assertEquals(7L, pendingEqual.pending.single().memo.version)

        val resolvedAhead = resolveConflictKeepLocalState(
            localMemos = listOf(local),
            cloudVersions = mapOf(local.id to 5L),
            pendingMutations = mapOf(
                local.id to PendingMemoMutation(
                    mutationId = "old-mutation",
                    memoVersion = local.version,
                    memoUpdatedAt = local.updatedAt,
                ),
            ),
            localMemo = local,
            serverVersion = 7L,
            newMutationId = { "resubmit-ahead" },
            now = "2026-07-30T03:00:00Z",
        )
        val keptAhead = resolvedAhead.memos.single()
        assertEquals(7L, resolvedAhead.cloudVersions[local.id])
        assertEquals(8L, keptAhead.version)
        assertEquals("本机冲突内容", keptAhead.content)

        val pendingAhead = resolvePendingMemoSyncs(
            memos = resolvedAhead.memos,
            cloudVersions = resolvedAhead.cloudVersions,
            pendingMutations = resolvedAhead.pendingMutations,
            newMutationId = { error("should reuse mutation") },
        )
        assertEquals(1, pendingAhead.pending.size)
        assertEquals(7L, pendingAhead.pending.single().baseVersion)
        assertEquals("resubmit-ahead", pendingAhead.pending.single().mutationId)
        assertEquals(8L, pendingAhead.pending.single().memo.version)
    }

    @Test
    fun takeServerConflictDropsPendingAndUsesServerMemo() {
        val local = memo(version = 3, content = "本机待推送")
        val server = memo(version = 2, content = "服务端版本")
        val resolved = resolveConflictTakeServerState(
            localMemos = listOf(local),
            cloudVersions = mapOf(local.id to 1L),
            pendingMutations = mapOf(
                local.id to PendingMemoMutation(
                    mutationId = "old-mutation",
                    memoVersion = local.version,
                    memoUpdatedAt = local.updatedAt,
                ),
            ),
            serverMemo = server,
        )
        assertEquals(server, resolved.memos.single())
        assertEquals(2L, resolved.cloudVersions[server.id])
        assertTrue(resolved.pendingMutations.isEmpty())
    }

    @Test
    fun canonicalRollbackClearsAppliedMutationAndNextLocalVersionGetsNewId() {
        val localV3 = memo(
            version = 3,
            content = "第一次本地修改",
            updatedAt = "2026-07-10T03:00:00Z",
        )
        val first = resolvePendingMemoSyncs(
            memos = listOf(localV3),
            cloudVersions = mapOf(localV3.id to 2L),
            pendingMutations = emptyMap(),
            newMutationId = { "mutation-a" },
        )
        val retry = resolvePendingMemoSyncs(
            memos = listOf(localV3),
            cloudVersions = mapOf(localV3.id to 2L),
            pendingMutations = first.pendingMutations,
            newMutationId = { error("重试不应生成新 mutationId") },
        )
        assertEquals("mutation-a", retry.pending.single().mutationId)

        val canonicalV2 = memo(
            version = 2,
            content = "服务端规范内容",
            updatedAt = "2026-07-10T03:30:00Z",
        )
        val merged = mergeAppliedCloudMemos(
            localMemos = listOf(localV3),
            cloudVersions = mapOf(localV3.id to 2L),
            pendingMutations = first.pendingMutations,
            appliedMemos = listOf(
                AppliedMemoSync(
                    mutationId = "mutation-a",
                    memo = canonicalV2,
                ),
            ),
        )

        assertEquals(canonicalV2, merged.memos.single())
        assertEquals(2L, merged.cloudVersions[canonicalV2.id])
        assertTrue(merged.pendingMutations.isEmpty())

        val localV3Again = canonicalV2.copy(
            content = "第二次本地修改",
            version = 3,
            updatedAt = "2026-07-10T04:00:00Z",
        )
        val next = resolvePendingMemoSyncs(
            memos = listOf(localV3Again),
            cloudVersions = merged.cloudVersions,
            pendingMutations = merged.pendingMutations,
            newMutationId = { "mutation-b" },
        )

        assertEquals("mutation-b", next.pending.single().mutationId)
        assertNotEquals(first.pending.single().mutationId, next.pending.single().mutationId)
    }

    @Test
    fun oldCanonicalResponseDoesNotOverwriteNewerLocalMutation() {
        val sentMemo = memo(
            version = 3,
            content = "已发送的本地修改",
            updatedAt = "2026-07-10T03:00:00Z",
        )
        val sent = resolvePendingMemoSyncs(
            memos = listOf(sentMemo),
            cloudVersions = mapOf(sentMemo.id to 2L),
            pendingMutations = emptyMap(),
            newMutationId = { "mutation-old" },
        )
        val newerLocal = sentMemo.copy(
            content = "push 后的新修改",
            version = 4,
            updatedAt = "2026-07-10T04:00:00Z",
        )
        val current = resolvePendingMemoSyncs(
            memos = listOf(newerLocal),
            cloudVersions = mapOf(sentMemo.id to 2L),
            pendingMutations = sent.pendingMutations,
            newMutationId = { "mutation-new" },
        )
        val oldCanonical = memo(
            version = 3,
            content = "旧 push 的服务端结果",
            updatedAt = "2026-07-10T03:30:00Z",
        )

        val merged = mergeAppliedCloudMemos(
            localMemos = listOf(newerLocal),
            cloudVersions = mapOf(sentMemo.id to 2L),
            pendingMutations = current.pendingMutations,
            appliedMemos = listOf(
                AppliedMemoSync(
                    mutationId = "mutation-old",
                    memo = oldCanonical,
                ),
            ),
        )

        assertEquals(newerLocal, merged.memos.single())
        assertEquals(3L, merged.cloudVersions[newerLocal.id])
        assertEquals("mutation-new", merged.pendingMutations[newerLocal.id]?.mutationId)

        val retry = resolvePendingMemoSyncs(
            memos = merged.memos,
            cloudVersions = merged.cloudVersions,
            pendingMutations = merged.pendingMutations,
            newMutationId = { error("新修改重试不应更换 mutationId") },
        )
        assertEquals("mutation-new", retry.pending.single().mutationId)
        assertEquals(3L, retry.pending.single().baseVersion)
    }

    @Test
    fun pullAfterConflictKeepsPendingExistingMemoAndItsPreviousCloudBaseline() {
        val local = memo(
            version = 3,
            content = "待解决的本地修改",
            updatedAt = "2026-07-10T03:00:00Z",
        )
        val pending = PendingMemoMutation(
            mutationId = "mutation-local",
            memoVersion = local.version,
            memoUpdatedAt = local.updatedAt,
        )
        val server = memo(
            version = 2,
            content = "服务端冲突版本",
            updatedAt = "2026-07-10T02:30:00Z",
        )

        val merged = mergePulledCloudMemos(
            localMemos = listOf(local),
            pulledMemos = listOf(server),
            cloudVersions = mapOf(local.id to 1L),
            pendingMutations = mapOf(local.id to pending),
        )

        assertEquals(local, merged.memos.single())
        assertEquals(1L, merged.cloudVersions[local.id])
        assertEquals(pending, merged.pendingMutations[local.id])
    }

    @Test
    fun pullAfterRejectedCreateKeepsPendingMemoWhenServerDoesNotContainIt() {
        val local = memo(
            version = 1,
            content = "尚未上传的新记录",
        ).copy(id = "memo-new")
        val pending = PendingMemoMutation(
            mutationId = "mutation-create",
            memoVersion = local.version,
            memoUpdatedAt = local.updatedAt,
        )

        val merged = mergePulledCloudMemos(
            localMemos = listOf(local),
            pulledMemos = emptyList(),
            cloudVersions = emptyMap(),
            pendingMutations = mapOf(local.id to pending),
        )

        assertEquals(local, merged.memos.single())
        assertEquals(null, merged.cloudVersions[local.id])
        assertEquals(pending, merged.pendingMutations[local.id])
    }

    @Test
    fun pullAppliesCanonicalMemoAndUpdatesBaselineWithoutPendingMutation() {
        val local = memo(version = 1, content = "旧本地内容")
        val server = memo(
            version = 2,
            content = "服务端规范内容",
            updatedAt = "2026-07-10T02:30:00Z",
        )

        val merged = mergePulledCloudMemos(
            localMemos = listOf(local),
            pulledMemos = listOf(server),
            cloudVersions = mapOf(local.id to 1L),
            pendingMutations = emptyMap(),
        )

        assertEquals(server, merged.memos.single())
        assertEquals(2L, merged.cloudVersions[server.id])
        assertTrue(merged.pendingMutations.isEmpty())
    }

    private fun memo(
        version: Long,
        content: String,
        updatedAt: String = "2026-07-10T02:00:00Z",
    ): Memo {
        return Memo(
            id = "memo-1",
            content = content,
            entryDate = "2026-07-10",
            version = version,
            createdAt = "2026-07-10T01:00:00Z",
            updatedAt = updatedAt,
            favoritedAt = null,
            archivedAt = null,
            deletedAt = null,
        )
    }

    private fun memoJson(content: String, version: Long): JSONObject {
        return JSONObject()
            .put("id", "memo-1")
            .put("content", content)
            .put("entryDate", "2026-07-10")
            .put("version", version)
            .put("createdAt", "2026-07-10T01:00:00Z")
            .put("updatedAt", "2026-07-10T02:00:00Z")
            .put("archivedAt", JSONObject.NULL)
            .put("deletedAt", JSONObject.NULL)
    }
}
