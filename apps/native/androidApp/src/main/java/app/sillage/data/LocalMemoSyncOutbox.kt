package app.sillage.data

import app.sillage.core.sync.AppliedMemoSync
import app.sillage.core.sync.MemoSyncOutbox
import app.sillage.core.sync.PendingMemoSync

/** Android transactional local outbox adapter. */
class LocalMemoSyncOutbox(
    private val localDataStore: LocalDataStore,
) : MemoSyncOutbox {
    override suspend fun pendingMemos(): List<PendingMemoSync> {
        return localDataStore.pendingCloudMemos()
    }

    override suspend fun applySyncedMemos(applied: List<AppliedMemoSync>) {
        localDataStore.applyCloudSyncedMemos(applied)
    }
}
