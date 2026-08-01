package app.sillage.data

import app.sillage.core.domain.records.Memo
import app.sillage.core.sync.ConflictMemoSync
import app.sillage.core.sync.MemoSyncConflictRepository

/** Android transactional conflict-resolution adapter. */
class LocalMemoSyncConflictRepository(
    private val localDataStore: LocalDataStore,
) : MemoSyncConflictRepository {
    override suspend fun keepLocal(conflict: ConflictMemoSync) {
        localDataStore.resolveConflictKeepLocal(conflict)
    }

    override suspend fun takeServer(conflict: ConflictMemoSync) {
        localDataStore.resolveConflictTakeServer(conflict)
    }

    override fun localMemo(resourceId: String): Memo? {
        return localDataStore.getMemoOrNull(resourceId)
    }
}
