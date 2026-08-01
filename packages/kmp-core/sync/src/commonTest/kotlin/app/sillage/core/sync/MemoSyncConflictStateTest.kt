package app.sillage.core.sync

import app.sillage.core.domain.records.Memo
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MemoSyncConflictStateTest {
    @Test
    fun stateFindsAndRemovesConflictByResourceIdentity() {
        val first = item("memo-1")
        val second = item("memo-2")
        val state = MemoSyncConflictStateHolder(listOf(first, second))

        assertEquals(first, state.find("memo-1"))
        assertEquals(listOf(second), state.remove("memo-1").items)
        assertNull(state.find("missing"))
    }

    @Test
    fun resolutionDispatchesExplicitUserChoice() {
        val repository = CapturingRepository()
        val useCase = ResolveMemoSyncConflictUseCase(repository)
        val conflict = conflict("memo-1")

        runImmediate { useCase(ResolveMemoSyncConflictCommand.KeepLocal(conflict)) }
        runImmediate { useCase(ResolveMemoSyncConflictCommand.TakeServer(conflict)) }

        assertEquals(listOf("keep:memo-1", "take:memo-1"), repository.operations)
        assertEquals(memo("memo-1"), useCase.item(conflict).localMemo)
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

    private fun item(resourceId: String): MemoSyncConflictItem {
        return MemoSyncConflictItem(conflict(resourceId), memo(resourceId))
    }

    private fun conflict(resourceId: String): ConflictMemoSync {
        return ConflictMemoSync(
            mutationId = "mutation-$resourceId",
            resourceId = resourceId,
            clientVersion = 1,
            serverVersion = 2,
            serverMemo = memo(resourceId).copy(version = 2),
        )
    }

    private fun memo(id: String): Memo {
        return Memo(
            id = id,
            content = id,
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
