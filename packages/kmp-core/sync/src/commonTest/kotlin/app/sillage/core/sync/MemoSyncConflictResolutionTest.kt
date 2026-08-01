package app.sillage.core.sync

import app.sillage.core.domain.records.Memo
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals

class MemoSyncConflictResolutionTest {
    @Test
    fun dispatchesExplicitResolutionCommands() = runSuspend {
        val repository = CapturingRepository()
        val useCase = ResolveMemoSyncConflictUseCase(repository)
        val conflict = conflict("memo-1")

        useCase(ResolveMemoSyncConflictCommand.KeepLocal(conflict))
        useCase(ResolveMemoSyncConflictCommand.TakeServer(conflict))

        assertEquals(listOf("keep:memo-1", "take:memo-1"), repository.operations)
        assertEquals(memo("memo-1"), useCase.localMemo(conflict))
    }

    private inner class CapturingRepository : MemoSyncConflictRepository {
        val operations = mutableListOf<String>()

        override suspend fun keepLocal(conflict: ConflictMemoSync) {
            operations += "keep:${conflict.resourceId}"
        }

        override suspend fun takeServer(conflict: ConflictMemoSync) {
            operations += "take:${conflict.resourceId}"
        }

        override fun localMemo(resourceId: String): Memo = memo(resourceId)
    }

    private fun conflict(resourceId: String) = ConflictMemoSync(
        mutationId = "mutation-$resourceId",
        resourceId = resourceId,
        clientVersion = 1,
        serverVersion = 2,
        serverMemo = memo(resourceId).copy(version = 2),
    )

    private fun memo(id: String) = Memo(
        id = id,
        content = "content",
        entryDate = "2026-08-01",
        version = 1,
        createdAt = "2026-08-01T00:00:00Z",
        updatedAt = "2026-08-01T00:00:00Z",
        favoritedAt = null,
        archivedAt = null,
        deletedAt = null,
        purgedAt = null,
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
