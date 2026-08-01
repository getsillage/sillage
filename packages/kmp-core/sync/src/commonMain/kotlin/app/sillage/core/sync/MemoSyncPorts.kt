package app.sillage.core.sync

interface MemoSyncOutbox {
    suspend fun pendingMemos(): List<PendingMemoSync>

    suspend fun applySyncedMemos(applied: List<AppliedMemoSync>)
}

interface MemoSyncGateway {
    suspend fun pushMemos(pending: List<PendingMemoSync>): SyncPushSummary
}
