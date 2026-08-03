package app.sillage.core.sync

import app.sillage.core.domain.records.Memo

interface MemoSyncOutbox {
    suspend fun pendingMemos(): List<PendingMemoSync>

    suspend fun applySyncedMemos(applied: List<AppliedMemoSync>)
}

interface MemoSyncGateway {
    suspend fun pushMemos(pending: List<PendingMemoSync>): SyncPushSummary

    suspend fun pullMemos(): List<Memo>
}
