package app.sillage.core.sync

class PushPendingMemosUseCase(
    private val outbox: MemoSyncOutbox,
    private val gateway: MemoSyncGateway,
) {
    suspend operator fun invoke(): SyncPushSummary {
        val pending = outbox.pendingMemos()
        if (pending.isEmpty()) {
            return SyncPushSummary(applied = 0, conflict = 0, rejected = 0)
        }
        val summary = gateway.pushMemos(pending)
        outbox.applySyncedMemos(summary.appliedMemoSyncs)
        return summary
    }
}
