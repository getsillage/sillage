package app.sillage.core.application.records

import app.sillage.core.domain.records.Memo
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals

class MutateRecordLifecycleUseCaseTest {
    @Test
    fun dispatchesEveryLifecycleCommandWithoutChangingIntent() {
        val repository = CapturingRepository()
        val useCase = MutateRecordLifecycleUseCase(repository)
        val memo = memo()
        val commands = listOf(
            RecordLifecycleCommand.SetFavorited(memo, true),
            RecordLifecycleCommand.SetArchived(memo, false),
            RecordLifecycleCommand.Delete(memo),
            RecordLifecycleCommand.Restore(memo),
            RecordLifecycleCommand.Purge(memo),
        )

        commands.forEach { command ->
            runImmediate { useCase(command) }
        }

        assertEquals(
            listOf("favorite:true", "archive:false", "delete", "restore", "purge"),
            repository.operations,
        )
    }

    private inner class CapturingRepository : RecordLifecycleRepository {
        val operations = mutableListOf<String>()

        override suspend fun setRecordFavorited(memo: Memo, favorited: Boolean): Memo {
            operations += "favorite:$favorited"
            return memo
        }

        override suspend fun setRecordArchived(memo: Memo, archived: Boolean): Memo {
            operations += "archive:$archived"
            return memo
        }

        override suspend fun deleteRecord(memo: Memo): Memo {
            operations += "delete"
            return memo
        }

        override suspend fun restoreRecord(memo: Memo): Memo {
            operations += "restore"
            return memo
        }

        override suspend fun purgeRecord(memo: Memo): Memo {
            operations += "purge"
            return memo
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
