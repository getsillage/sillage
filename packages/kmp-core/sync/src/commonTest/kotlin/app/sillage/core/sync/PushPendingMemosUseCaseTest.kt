package app.sillage.core.sync

import app.sillage.core.domain.records.Memo
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PushPendingMemosUseCaseTest {
    @Test
    fun emptyOutboxDoesNotCallRemoteGateway() {
        val outbox = FakeOutbox(emptyList())
        val gateway = FakeGateway()

        val summary = runImmediate { PushPendingMemosUseCase(outbox, gateway)() }

        assertEquals(0, summary.applied)
        assertFalse(gateway.called)
        assertEquals(emptyList(), outbox.applied)
    }

    @Test
    fun onlyAppliedResultsAreAcknowledgedLocally() {
        val pending = PendingMemoSync(memo(), baseVersion = 1, mutationId = "mutation-1")
        val applied = AppliedMemoSync(pending.mutationId, pending.memo.copy(version = 2))
        val outbox = FakeOutbox(listOf(pending))
        val gateway = FakeGateway(
            result = SyncPushSummary(
                applied = 1,
                conflict = 1,
                rejected = 0,
                appliedMemoSyncs = listOf(applied),
            ),
        )

        val summary = runImmediate { PushPendingMemosUseCase(outbox, gateway)() }

        assertEquals(1, summary.conflict)
        assertEquals(listOf(applied), outbox.applied)
    }

    private class FakeOutbox(
        private val pending: List<PendingMemoSync>,
    ) : MemoSyncOutbox {
        var applied: List<AppliedMemoSync> = emptyList()

        override suspend fun pendingMemos(): List<PendingMemoSync> = pending

        override suspend fun applySyncedMemos(applied: List<AppliedMemoSync>) {
            this.applied = applied
        }
    }

    private class FakeGateway(
        private val result: SyncPushSummary = SyncPushSummary(0, 0, 0),
    ) : MemoSyncGateway {
        var called = false

        override suspend fun pushMemos(pending: List<PendingMemoSync>): SyncPushSummary {
            called = true
            return result
        }
    }

    private fun memo(): Memo {
        return Memo(
            id = "memo-1",
            content = "content",
            entryDate = "2026-08-01",
            version = 1,
            createdAt = "2026-08-01T00:00:00Z",
            updatedAt = "2026-08-01T00:00:00Z",
            favoritedAt = null,
            archivedAt = null,
            deletedAt = null,
        )
    }

    private fun <T> runImmediate(block: suspend () -> T): T {
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
