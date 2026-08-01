package app.sillage.core.application.records

import app.sillage.core.domain.records.Memo
import app.sillage.core.domain.records.MemoAI
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals

class GetRecordDetailUseCaseTest {
    @Test
    fun forwardsIdentityAndReturnsDomainDetail() {
        val expected = RecordDetail(
            memo = memo("memo-1"),
            ai = memoAI("memo-1"),
        )
        var capturedMemoId: String? = null
        val useCase = GetRecordDetailUseCase(
            repository = object : RecordDetailRepository {
                override suspend fun getRecordDetail(memoId: String): RecordDetail {
                    capturedMemoId = memoId
                    return expected
                }
            },
        )

        val result = runImmediate { useCase("memo-1") }

        assertEquals("memo-1", capturedMemoId)
        assertEquals(expected, result)
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

    private fun memoAI(memoId: String): MemoAI {
        return MemoAI(
            memoId = memoId,
            summary = "Summary",
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
            inputTokens = 10,
            outputTokens = 5,
            totalTokens = 15,
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
