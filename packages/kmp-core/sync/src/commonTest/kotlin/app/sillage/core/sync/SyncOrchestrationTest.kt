package app.sillage.core.sync

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncOrchestrationTest {
    @Test
    fun pushPreparesAttachmentsBeforeReadingOutbox() = runSuspend {
        val events = mutableListOf<String>()
        val push = RunSyncPushUseCase(
            preparation = SyncPushPreparation { events += "prepare" },
            pushPendingMemos = PushPendingMemosUseCase(
                outbox = RecordingOutbox(events),
                gateway = EmptyGateway,
            ),
        )

        push()

        assertEquals(listOf("prepare", "pending"), events)
    }

    @Test
    fun twoWaySyncCompletesPushBeforePull() = runSuspend {
        val events = mutableListOf<String>()
        val push = RunSyncPushUseCase(
            preparation = SyncPushPreparation { events += "prepare" },
            pushPendingMemos = PushPendingMemosUseCase(
                outbox = RecordingOutbox(events),
                gateway = EmptyGateway,
            ),
        )
        val pull = PullSyncUseCase(
            gateway = object : SyncSnapshotGateway {
                override suspend fun pull(): SyncSnapshot {
                    events += "pull"
                    return emptySnapshot()
                }
            },
            repository = object : SyncSnapshotRepository {
                override suspend fun merge(snapshot: SyncSnapshot) {
                    events += "merge"
                }
            },
        )

        RunTwoWaySyncUseCase(push, pull)()

        assertEquals(listOf("prepare", "pending", "pull", "merge"), events)
    }

    private class RecordingOutbox(
        private val events: MutableList<String>,
    ) : MemoSyncOutbox {
        override suspend fun pendingMemos(): List<PendingMemoSync> {
            events += "pending"
            return emptyList()
        }

        override suspend fun applySyncedMemos(applied: List<AppliedMemoSync>) = Unit
    }

private object EmptyGateway : MemoSyncGateway {
    override suspend fun pullMemos() = emptyList<app.sillage.core.domain.records.Memo>()

    override suspend fun pushMemos(pending: List<PendingMemoSync>): SyncPushSummary = SyncPushSummary(
            applied = 0,
            conflict = 0,
            rejected = 0,
        )
    }

    private fun emptySnapshot() = SyncSnapshot(
        memos = emptyList(),
        memoAI = emptyList(),
        aiSettings = SyncAISettingsSection.Unavailable,
        askConversations = emptyList(),
        askMessages = emptyList(),
    )

    private fun <T> runSuspend(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(
            object : Continuation<T> {
                override val context = EmptyCoroutineContext

                override fun resumeWith(result: Result<T>) {
                    outcome = result
                }
            },
        )
        return checkNotNull(outcome) { "Test coroutine did not complete synchronously" }.getOrThrow()
    }
}
