package app.sillage.features.records

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecordsPaginationStateHolderTest {
    @Test
    fun unavailableEmptyOrBusyStateDoesNotStartAnotherPage() {
        assertNull(RecordsPaginationStateHolder().nextRequest(context()))
        assertNull(
            RecordsPaginationStateHolder(nextCursor = "cursor")
                .nextRequest(context(sourceAvailable = false)),
        )
        assertNull(
            RecordsPaginationStateHolder(nextCursor = "cursor", loadingMore = true)
                .nextRequest(context()),
        )
    }

    @Test
    fun requestMustBeginFromTheExactCurrentState() {
        val state = RecordsPaginationStateHolder(nextCursor = "cursor", requestId = 4)
        val request = assertNotNull(state.nextRequest(context()))

        assertEquals(5, request.requestId)
        assertEquals(
            RecordsPaginationStateHolder(nextCursor = "cursor", loadingMore = true, requestId = 5),
            state.begin(request, context()),
        )
        assertNull(state.begin(request.copy(cursor = "other"), context()))
    }

    @Test
    fun lateResponseCannotCrossAnyQueryBoundary() {
        val initial = RecordsPaginationStateHolder(nextCursor = "cursor")
        val request = assertNotNull(initial.nextRequest(context()))
        val loading = assertNotNull(initial.begin(request, context()))

        assertTrue(loading.canApply(request, context()))
        assertFalse(loading.canApply(request, context(sourceKey = "offline")))
        assertFalse(loading.canApply(request, context(clientContextGeneration = 2)))
        assertFalse(loading.canApply(request, context(filter = MemoListFilter.Archived)))
        assertFalse(loading.canApply(request, context(cacheGeneration = 2)))
        assertFalse(loading.copy(nextCursor = "other").canApply(request, context()))
    }

    @Test
    fun completeFailureAndCancellationAreExplicitTransitions() {
        val initial = RecordsPaginationStateHolder(nextCursor = "cursor", requestId = 2)
        val request = assertNotNull(initial.nextRequest(context()))
        val loading = assertNotNull(initial.begin(request, context()))

        assertEquals(
            RecordsPaginationStateHolder(nextCursor = "next", requestId = 3),
            loading.complete(request, context(), nextCursor = "next"),
        )
        assertEquals(
            RecordsPaginationStateHolder(nextCursor = "cursor", requestId = 3),
            loading.fail(request, context()),
        )
        assertEquals(
            RecordsPaginationStateHolder(nextCursor = "cursor", requestId = 4),
            loading.cancel(),
        )
    }

    private fun context(
        sourceKey: String = "online",
        sourceAvailable: Boolean = true,
        clientContextGeneration: Long = 1,
        filter: MemoListFilter = MemoListFilter.Unarchived,
        cacheGeneration: Long = 1,
    ): RecordsPageContext {
        return RecordsPageContext(
            sourceKey = sourceKey,
            sourceAvailable = sourceAvailable,
            clientContextGeneration = clientContextGeneration,
            filter = filter,
            cacheGeneration = cacheGeneration,
        )
    }
}
