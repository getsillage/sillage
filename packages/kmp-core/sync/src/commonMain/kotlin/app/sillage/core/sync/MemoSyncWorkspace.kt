package app.sillage.core.sync

import app.sillage.core.domain.records.Memo

const val MEMO_SYNC_ACTION_CREATE = "create"
const val MEMO_SYNC_ACTION_UPDATE = "update"
const val MEMO_SYNC_ACTION_DELETE = "delete"
const val MEMO_SYNC_ACTION_RESTORE = "restore"
const val MEMO_SYNC_ACTION_PURGE = "purge"

val SupportedMemoSyncActions = setOf(
    MEMO_SYNC_ACTION_CREATE,
    MEMO_SYNC_ACTION_UPDATE,
    MEMO_SYNC_ACTION_DELETE,
    MEMO_SYNC_ACTION_RESTORE,
    MEMO_SYNC_ACTION_PURGE,
)

interface MemoSyncGatewayFactory {
    fun createMemoSyncGateway(baseUrl: String): MemoSyncGateway
}

interface MemoSyncWorkspaceFactory {
    fun createMemoSyncWorkspace(baseUrl: String): MemoSyncWorkspace
}

interface MemoSyncWorkspace : MemoSyncOutbox, MemoSyncConflictRepository {
    suspend fun mergePulledMemos(memos: List<Memo>): Int
}

class MemoSyncServerMismatchException(
    val boundBaseUrl: String,
    val requestedBaseUrl: String,
) : IllegalStateException(
    "Local memo synchronization is bound to $boundBaseUrl, not $requestedBaseUrl.",
)

data class PendingMemoMutation(
    val mutationId: String,
    val memoVersion: Long,
    val memoUpdatedAt: String,
    val action: String = "",
) {
    fun matches(memo: Memo): Boolean {
        return memoVersion == memo.version && memoUpdatedAt == memo.updatedAt
    }
}

data class PendingMemoSyncResolution(
    val pending: List<PendingMemoSync>,
    val pendingMutations: Map<String, PendingMemoMutation>,
)

fun resolvePendingMemoSyncs(
    memos: List<Memo>,
    cloudVersions: Map<String, Long>,
    pendingMutations: Map<String, PendingMemoMutation>,
    newMutationId: () -> String,
): PendingMemoSyncResolution {
    val resolvedMutations = pendingMutations.toMutableMap()
    val pending = memos.mapNotNull { memo ->
        val cloudVersion = cloudVersions[memo.id]
        val hasNoRemoteResource = cloudVersion == null
        if (hasNoRemoteResource && (memo.deletedAt != null || memo.purgedAt != null)) {
            resolvedMutations.remove(memo.id)
            return@mapNotNull null
        }
        if (cloudVersion != null && memo.version <= cloudVersion) {
            resolvedMutations.remove(memo.id)
            return@mapNotNull null
        }

        val mutation = resolvedMutations[memo.id]
            ?.takeIf { it.matches(memo) }
            ?: PendingMemoMutation(
                mutationId = newMutationId(),
                memoVersion = memo.version,
                memoUpdatedAt = memo.updatedAt,
            ).also { resolvedMutations[memo.id] = it }
        PendingMemoSync(
            memo = memo,
            baseVersion = cloudVersion,
            mutationId = mutation.mutationId,
            action = mutation.action.ifBlank { defaultMemoSyncAction(memo, cloudVersion) },
        )
    }
    val memoIds = memos.mapTo(mutableSetOf(), Memo::id)
    resolvedMutations.keys.retainAll(memoIds)
    return PendingMemoSyncResolution(
        pending = pending,
        pendingMutations = resolvedMutations,
    )
}

data class MemoSyncStateUpdate(
    val memos: List<Memo>,
    val cloudVersions: Map<String, Long>,
    val pendingMutations: Map<String, PendingMemoMutation>,
)

data class PulledMemoSyncMerge(
    val state: MemoSyncStateUpdate,
    val changedMemos: Int,
)

fun mergePulledMemoSyncs(
    localMemos: List<Memo>,
    cloudVersions: Map<String, Long>,
    pendingMutations: Map<String, PendingMemoMutation>,
    serverMemos: List<Memo>,
): PulledMemoSyncMerge {
    val mergedMemos = linkedMapOf<String, Memo>()
    localMemos.forEach { memo -> mergedMemos[memo.id] = memo }
    val mergedVersions = cloudVersions.toMutableMap()
    var changedMemos = 0

    serverMemos.forEach { serverMemo ->
        if (pendingMutations.containsKey(serverMemo.id)) return@forEach

        val cloudVersion = mergedVersions[serverMemo.id]
        if (cloudVersion != null && serverMemo.version < cloudVersion) return@forEach

        if (mergedMemos[serverMemo.id] != serverMemo) {
            mergedMemos[serverMemo.id] = serverMemo
            changedMemos += 1
        }
        mergedVersions[serverMemo.id] = maxOf(cloudVersion ?: 0L, serverMemo.version)
    }

    return PulledMemoSyncMerge(
        state = MemoSyncStateUpdate(
            memos = mergedMemos.values.toList(),
            cloudVersions = mergedVersions,
            pendingMutations = pendingMutations,
        ),
        changedMemos = changedMemos,
    )
}

fun mergeAppliedMemoSyncs(
    localMemos: List<Memo>,
    cloudVersions: Map<String, Long>,
    pendingMutations: Map<String, PendingMemoMutation>,
    appliedMemos: List<AppliedMemoSync>,
    newMutationId: () -> String,
    currentTimestamp: () -> String,
): MemoSyncStateUpdate {
    val mergedMemos = linkedMapOf<String, Memo>()
    localMemos.forEach { memo -> mergedMemos[memo.id] = memo }
    val mergedVersions = cloudVersions.toMutableMap()
    val mergedMutations = pendingMutations.toMutableMap()

    appliedMemos.forEach { applied ->
        val serverMemo = applied.memo
        val localMemo = mergedMemos[serverMemo.id]
        val pendingMutation = mergedMutations[serverMemo.id]
        val stillCurrent = pendingMutation?.mutationId == applied.mutationId &&
            localMemo != null &&
            pendingMutation.matches(localMemo)
        if (stillCurrent && pendingMutation.action == MEMO_SYNC_ACTION_RESTORE &&
            localMemo.requiresUpdateAfterRestore(serverMemo)
        ) {
            val nextVersion = maxOf(localMemo.version, serverMemo.version + 1)
            val retainedLocal = if (nextVersion == localMemo.version) {
                localMemo
            } else {
                localMemo.copy(version = nextVersion, updatedAt = currentTimestamp())
            }
            mergedMemos[serverMemo.id] = retainedLocal
            mergedVersions[serverMemo.id] = serverMemo.version
            mergedMutations[serverMemo.id] = PendingMemoMutation(
                mutationId = newMutationId(),
                memoVersion = retainedLocal.version,
                memoUpdatedAt = retainedLocal.updatedAt,
                action = MEMO_SYNC_ACTION_UPDATE,
            )
        } else if (stillCurrent) {
            mergedMemos[serverMemo.id] = serverMemo
            mergedVersions[serverMemo.id] = serverMemo.version
            mergedMutations.remove(serverMemo.id)
        } else {
            mergedVersions[serverMemo.id] = maxOf(
                mergedVersions[serverMemo.id] ?: 0L,
                serverMemo.version,
            )
        }
    }

    return MemoSyncStateUpdate(
        memos = mergedMemos.values.toList(),
        cloudVersions = mergedVersions,
        pendingMutations = mergedMutations,
    )
}

fun resolveMemoSyncConflictKeepLocal(
    localMemos: List<Memo>,
    cloudVersions: Map<String, Long>,
    pendingMutations: Map<String, PendingMemoMutation>,
    localMemo: Memo,
    serverVersion: Long,
    newMutationId: () -> String,
    currentTimestamp: () -> String,
): MemoSyncStateUpdate {
    require(serverVersion > 0L) { "The server memo version must be positive." }
    val versions = cloudVersions.toMutableMap()
    versions[localMemo.id] = serverVersion
    val resubmitVersion = maxOf(localMemo.version, serverVersion + 1)
    val retainedLocal = if (resubmitVersion == localMemo.version) {
        localMemo
    } else {
        localMemo.copy(version = resubmitVersion, updatedAt = currentTimestamp())
    }
    val mutations = pendingMutations.toMutableMap()
    mutations[localMemo.id] = PendingMemoMutation(
        mutationId = newMutationId(),
        memoVersion = retainedLocal.version,
        memoUpdatedAt = retainedLocal.updatedAt,
        action = pendingMutations[localMemo.id]?.action.orEmpty(),
    )
    return MemoSyncStateUpdate(
        memos = localMemos.map { if (it.id == localMemo.id) retainedLocal else it },
        cloudVersions = versions,
        pendingMutations = mutations,
    )
}

fun resolveMemoSyncConflictTakeServer(
    localMemos: List<Memo>,
    cloudVersions: Map<String, Long>,
    pendingMutations: Map<String, PendingMemoMutation>,
    serverMemo: Memo,
): MemoSyncStateUpdate {
    val versions = cloudVersions.toMutableMap()
    versions[serverMemo.id] = serverMemo.version
    val mutations = pendingMutations.toMutableMap()
    mutations.remove(serverMemo.id)
    val memos = if (localMemos.any { it.id == serverMemo.id }) {
        localMemos.map { if (it.id == serverMemo.id) serverMemo else it }
    } else {
        localMemos + serverMemo
    }
    return MemoSyncStateUpdate(
        memos = memos,
        cloudVersions = versions,
        pendingMutations = mutations,
    )
}

private fun defaultMemoSyncAction(memo: Memo, cloudVersion: Long?): String {
    return when {
        memo.purgedAt != null -> MEMO_SYNC_ACTION_PURGE
        memo.deletedAt != null -> MEMO_SYNC_ACTION_DELETE
        cloudVersion == null -> MEMO_SYNC_ACTION_CREATE
        else -> MEMO_SYNC_ACTION_UPDATE
    }
}

private fun Memo.requiresUpdateAfterRestore(serverMemo: Memo): Boolean {
    if (deletedAt != null || purgedAt != null) return false
    return content != serverMemo.content ||
        entryDate != serverMemo.entryDate ||
        (favoritedAt != null) != (serverMemo.favoritedAt != null) ||
        (archivedAt != null) != (serverMemo.archivedAt != null)
}
