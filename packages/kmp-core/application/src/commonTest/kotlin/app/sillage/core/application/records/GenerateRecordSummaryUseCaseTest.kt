package app.sillage.core.application.records

import app.sillage.core.domain.records.Memo
import app.sillage.core.domain.records.MemoAI
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals

class GenerateRecordSummaryUseCaseTest {
    @Test
    fun generationAndPersistenceStaySeparate() {
        val repository = CapturingRepository()
        val memo = memo()
        val generated = runImmediate { GenerateRecordSummaryUseCase(repository)(memo) }

        assertEquals(memo, repository.generatedFor)
        assertEquals(null, repository.saved)

        runImmediate { SaveRecordSummaryUseCase(repository)(generated) }
        assertEquals(generated, repository.saved)
    }

    private inner class CapturingRepository : RecordSummaryGenerator, RecordSummaryStore {
        var generatedFor: Memo? = null
        var saved: MemoAI? = null

        override suspend fun generateRecordSummary(memo: Memo): MemoAI {
            generatedFor = memo
            return summary(memo.id)
        }

        override suspend fun saveRecordSummary(summary: MemoAI) {
            saved = summary
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

    private fun summary(memoId: String): MemoAI {
        return MemoAI(
            memoId = memoId,
            summary = "summary",
            sentiment = null,
            provider = "openai-compatible",
            model = "model",
            profileId = "profile-1",
            promptVersion = "v1",
            sourceMemoIds = "[\"$memoId\"]",
            status = "completed",
            errorCode = null,
            startedAt = null,
            finishedAt = null,
            inputTokens = 1,
            outputTokens = 1,
            totalTokens = 2,
            createdAt = "2026-08-01T00:00:00Z",
            updatedAt = "2026-08-01T00:00:00Z",
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
