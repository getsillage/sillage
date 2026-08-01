package app.sillage.core.application.records

import app.sillage.core.domain.records.Memo
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals

class ListRecordsPageUseCaseTest {
    @Test
    fun forwardsSemanticQueryAndReturnsPlatformNeutralPage() {
        val expectedQuery = RecordsPageQuery(
            scope = RecordsQueryScope.Favorited,
            cursor = "cursor-1",
        )
        val expectedPage = RecordsPage(
            memos = listOf(memo("memo-1")),
            nextCursor = "cursor-2",
        )
        var capturedQuery: RecordsPageQuery? = null
        val useCase = ListRecordsPageUseCase(
            repository = object : RecordsPageRepository {
                override suspend fun listPage(query: RecordsPageQuery): RecordsPage {
                    capturedQuery = query
                    return expectedPage
                }
            },
        )

        val result = runImmediate { useCase(expectedQuery) }

        assertEquals(expectedQuery, capturedQuery)
        assertEquals(expectedPage, result)
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
