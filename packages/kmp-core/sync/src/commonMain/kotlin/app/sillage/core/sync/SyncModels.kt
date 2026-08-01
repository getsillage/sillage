package app.sillage.core.sync

import app.sillage.core.domain.records.Memo

data class PendingMemoSync(
    val memo: Memo,
    val baseVersion: Long?,
    val mutationId: String,
    val action: String = "",
)

data class AppliedMemoSync(
    val mutationId: String,
    val memo: Memo,
)

/** One push result rejected by the server because of a version conflict. */
data class ConflictMemoSync(
    val mutationId: String,
    val resourceId: String,
    val clientVersion: Long?,
    val serverVersion: Long?,
    val serverMemo: Memo?,
)

data class SyncPushSummary(
    val applied: Int,
    val conflict: Int,
    val rejected: Int,
    val appliedMemoSyncs: List<AppliedMemoSync> = emptyList(),
    val conflictMemoSyncs: List<ConflictMemoSync> = emptyList(),
) {
    val empty: Boolean
        get() = applied == 0 && conflict == 0 && rejected == 0
}
