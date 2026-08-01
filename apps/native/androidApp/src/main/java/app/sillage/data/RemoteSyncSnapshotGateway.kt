package app.sillage.data

import app.sillage.core.sync.SyncSnapshot
import app.sillage.core.sync.SyncSnapshotGateway

/** Android REST adapter for full synchronization snapshots. */
class RemoteSyncSnapshotGateway(
    private val api: SillageApi,
) : SyncSnapshotGateway {
    override suspend fun pull(): SyncSnapshot = api.pullFullSync()
}
