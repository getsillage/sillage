package app.sillage.data

import app.sillage.core.sync.MemoSyncGateway
import app.sillage.core.sync.PendingMemoSync
import app.sillage.core.sync.SyncPushSummary

/** Android HTTP adapter for pushing memo outbox entries. */
class RemoteMemoSyncGateway(
    private val api: SillageApi,
) : MemoSyncGateway {
    override suspend fun pullMemos() = api.pullFullSync().memos

    override suspend fun pushMemos(pending: List<PendingMemoSync>): SyncPushSummary {
        return api.pushMemos(pending)
    }
}
