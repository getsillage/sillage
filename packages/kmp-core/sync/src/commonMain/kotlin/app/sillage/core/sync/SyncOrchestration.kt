package app.sillage.core.sync

/** Platform hook that stages attachment bytes before their memo mutations push. */
fun interface SyncPushPreparation {
    suspend fun prepare()
}

class RunSyncPushUseCase(
    private val preparation: SyncPushPreparation,
    private val pushPendingMemos: PushPendingMemosUseCase,
) {
    suspend operator fun invoke(): SyncPushSummary {
        preparation.prepare()
        return pushPendingMemos()
    }
}

data class TwoWaySyncResult(
    val push: SyncPushSummary,
    val pull: PullSyncResult,
)

class RunTwoWaySyncUseCase(
    private val push: RunSyncPushUseCase,
    private val pull: PullSyncUseCase,
) {
    suspend operator fun invoke(): TwoWaySyncResult {
        val pushResult = push()
        val pullResult = pull()
        return TwoWaySyncResult(pushResult, pullResult)
    }
}
