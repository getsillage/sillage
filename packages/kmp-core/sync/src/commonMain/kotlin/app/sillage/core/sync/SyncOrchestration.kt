package app.sillage.core.sync

import kotlin.coroutines.cancellation.CancellationException

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

data class MemoTwoWaySyncResult(
    val push: SyncPushSummary,
    val pulledMemos: Int,
)

class MemoSyncPullFailedException(
    val push: SyncPushSummary,
    val pullFailure: Exception,
) : Exception("Memo pull failed after the push phase completed.", pullFailure)

class RunMemoTwoWaySyncUseCase(
    private val workspace: MemoSyncWorkspace,
    private val gateway: MemoSyncGateway,
) {
    suspend operator fun invoke(): MemoTwoWaySyncResult {
        val pushResult = PushPendingMemosUseCase(workspace, gateway)()
        val pulledMemos = try {
            workspace.mergePulledMemos(gateway.pullMemos())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw MemoSyncPullFailedException(pushResult, error)
        }
        return MemoTwoWaySyncResult(pushResult, pulledMemos)
    }
}
