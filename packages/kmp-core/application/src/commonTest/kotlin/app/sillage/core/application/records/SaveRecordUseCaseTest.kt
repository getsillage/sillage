package app.sillage.core.application.records

import app.sillage.core.domain.records.Memo
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SaveRecordUseCaseTest {
    @Test
    fun createCommandUsesCreateBoundary() {
        val repository = CapturingRepository()
        val draft = RecordDraft("new", "2026-08-01")

        val result = runImmediate {
            SaveRecordUseCase(repository)(SaveRecordCommand.Create(draft))
        }

        assertEquals(draft, repository.createdDraft)
        assertEquals(null, repository.updatedMemo)
        assertEquals("created", result.id)
    }

    @Test
    fun updateCommandPreservesCanonicalMemoIdentity() {
        val repository = CapturingRepository()
        val existing = memo("memo-1")
        val draft = RecordDraft("changed", "2026-08-02")

        val result = runImmediate {
            SaveRecordUseCase(repository)(SaveRecordCommand.Update(existing, draft))
        }

        assertEquals(existing, repository.updatedMemo)
        assertEquals(draft, repository.updatedDraft)
        assertEquals(null, repository.createdDraft)
        assertEquals("updated", result.id)
    }

    @Test
    fun invalidDraftNeverReachesWriteBoundary() {
        val repository = CapturingRepository()

        val error = assertFailsWith<InvalidRecordDraftException> {
            runImmediate {
                SaveRecordUseCase(repository)(
                    SaveRecordCommand.Create(RecordDraft("", "2026-08-01")),
                )
            }
        }

        assertEquals(RecordDraftValidationError.EmptyContent, error.validationError)
        assertEquals(null, repository.createdDraft)
        assertEquals(null, repository.updatedDraft)
    }

    private inner class CapturingRepository : RecordWriteRepository {
        var createdDraft: RecordDraft? = null
        var updatedMemo: Memo? = null
        var updatedDraft: RecordDraft? = null

        override suspend fun createRecord(draft: RecordDraft): Memo {
            createdDraft = draft
            return memo("created")
        }

        override suspend fun updateRecord(existing: Memo, draft: RecordDraft): Memo {
            updatedMemo = existing
            updatedDraft = draft
            return memo("updated")
        }
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
