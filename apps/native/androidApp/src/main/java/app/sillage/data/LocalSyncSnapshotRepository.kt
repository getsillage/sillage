package app.sillage.data

import app.sillage.core.sync.SyncSnapshot
import app.sillage.core.sync.SyncSnapshotRepository

/** Android transactional merge adapter for a completed sync snapshot. */
class LocalSyncSnapshotRepository(
    private val localDataStore: LocalDataStore,
) : SyncSnapshotRepository {
    override suspend fun merge(snapshot: SyncSnapshot) {
        localDataStore.mergeFromServer(snapshot)
    }
}
