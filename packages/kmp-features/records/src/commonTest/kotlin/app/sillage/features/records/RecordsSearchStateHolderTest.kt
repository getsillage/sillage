package app.sillage.features.records

import app.sillage.core.domain.records.Memo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecordsSearchStateHolderTest {
    @Test
    fun queryUpdatesAndClearInvalidatePendingRequests() {
        val initial = RecordsSearchStateHolder(
            results = listOf(memo("old")),
            resultQuery = "old",
            failureQuery = "failure",
            requestId = 3,
        )

        assertEquals(
            initial.copy(
                query = "new",
                failureQuery = "",
                requestId = 4,
                searching = true,
            ),
            initial.updateQuery("new"),
        )
        assertEquals(
            RecordsSearchStateHolder(requestId = 4),
            initial.updateQuery(""),
        )
        assertEquals(
            RecordsSearchStateHolder(requestId = 4),
            initial.clear(),
        )
    }

    @Test
    fun beginAndLateResponseValidationUseTheFullContext() {
        val initial = RecordsSearchStateHolder(query = " query ", requestId = 2)
        val request = assertNotNull(initial.nextRequest(context()))
        val searching = assertNotNull(initial.begin(request, context()))

        assertEquals("query", request.query)
        assertTrue(searching.canApply(request, context()))
        assertFalse(searching.canApply(request, context(sourceKey = "offline")))
        assertFalse(searching.canApply(request, context(clientContextGeneration = 2)))
        assertFalse(searching.canApply(request, context(filter = MemoListFilter.Archived)))
        assertFalse(searching.canApply(request, context(cacheGeneration = 2)))
        assertFalse(searching.copy(query = "other").canApply(request, context()))
        assertNull(initial.begin(request.copy(requestId = 9), context()))
    }

    @Test
    fun completionPublishesOnlyResultsForTheCurrentQuery() {
        val initial = RecordsSearchStateHolder(query = "query", completionEventId = 4)
        val request = assertNotNull(initial.nextRequest(context()))
        val searching = assertNotNull(initial.begin(request, context()))
        val results = listOf(memo("memo-1"), memo("memo-2"))
        val completed = assertNotNull(searching.complete(request, context(), results))

        assertEquals(results, completed.currentResults())
        assertEquals(CompletedRecordsSearch("query", 2), completed.completed())
        assertEquals(5, completed.completionEventId)
        assertFalse(completed.searching)
        assertNull(completed.copy(query = "other").currentResults())
    }

    @Test
    fun failureKeepsPriorDataButHidesItFromTheCurrentQuery() {
        val previous = listOf(memo("old"))
        val initial = RecordsSearchStateHolder(
            query = "new",
            results = previous,
            resultQuery = "old",
        )
        val request = assertNotNull(initial.nextRequest(context()))
        val searching = assertNotNull(initial.begin(request, context()))
        val failed = assertNotNull(searching.fail(request, context()))

        assertEquals(previous, failed.results)
        assertEquals("new", failed.failureQuery)
        assertNull(failed.currentResults())
        assertFalse(failed.searching)
    }

    @Test
    fun canonicalMemoUpdatesVisibleResultsAndInvalidatesPendingSearch() {
        val original = memo("memo-1")
        val updated = original.copy(content = "updated", version = 2)
        val state = RecordsSearchStateHolder(
            query = "query",
            results = listOf(original),
            resultQuery = "query",
            requestId = 7,
            searching = true,
        )

        val invalidated = state.invalidateForMemoChange(updated, MemoListFilter.Unarchived)

        assertEquals(listOf(updated), invalidated.results)
        assertEquals("", invalidated.resultQuery)
        assertEquals(8, invalidated.requestId)
        assertFalse(invalidated.searching)
    }

    private fun context(
        sourceKey: String = "online",
        clientContextGeneration: Long = 1,
        filter: MemoListFilter = MemoListFilter.Unarchived,
        cacheGeneration: Long = 1,
    ): RecordsSearchContext {
        return RecordsSearchContext(
            sourceKey = sourceKey,
            clientContextGeneration = clientContextGeneration,
            filter = filter,
            cacheGeneration = cacheGeneration,
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
}
