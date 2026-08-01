package app.sillage.features.records

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecordsRefreshStateHolderTest {
    @Test
    fun beginRequiresTheExactNextRequest() {
        val state = RecordsRefreshStateHolder(requestId = 4)
        val request = state.nextRequest(context())

        assertEquals(5, request.requestId)
        assertEquals(
            RecordsRefreshStateHolder(status = RecordsRefreshStatus.Loading, requestId = 5),
            state.begin(request, context()),
        )
        assertNull(state.begin(request.copy(sourceKey = "other"), context()))
    }

    @Test
    fun lateResponseCannotCrossAnyCapturedQueryBoundary() {
        val initial = RecordsRefreshStateHolder()
        val request = initial.nextRequest(context())
        val loading = assertNotNull(initial.begin(request, context()))

        assertTrue(loading.canApply(request, context()))
        assertFalse(loading.canApply(request, context(sourceKey = "offline")))
        assertFalse(loading.canApply(request, context(clientContextGeneration = 2)))
        assertFalse(loading.canApply(request, context(filter = MemoListFilter.Archived)))
        assertFalse(loading.canApply(request, context(cacheGeneration = 2)))
        assertFalse(loading.canApply(request, context(paginationRequestId = 2)))
        assertFalse(loading.copy(status = RecordsRefreshStatus.Idle).canApply(request, context()))
    }

    @Test
    fun newerRefreshSupersedesEarlierResponse() {
        val initial = RecordsRefreshStateHolder()
        val firstRequest = initial.nextRequest(context())
        val firstLoading = assertNotNull(initial.begin(firstRequest, context()))
        val secondRequest = firstLoading.nextRequest(context())
        val secondLoading = assertNotNull(firstLoading.begin(secondRequest, context()))

        assertFalse(secondLoading.canApply(firstRequest, context()))
        assertTrue(secondLoading.canApply(secondRequest, context()))
    }

    @Test
    fun completionFailureAndCancellationAreExplicitTransitions() {
        val initial = RecordsRefreshStateHolder(requestId = 2)
        val request = initial.nextRequest(context())
        val loading = assertNotNull(initial.begin(request, context()))

        assertEquals(
            RecordsRefreshStateHolder(status = RecordsRefreshStatus.Idle, requestId = 3),
            loading.complete(request, context()),
        )
        assertEquals(
            RecordsRefreshStateHolder(status = RecordsRefreshStatus.Failed, requestId = 3),
            loading.fail(request, context()),
        )
        assertEquals(
            RecordsRefreshStateHolder(status = RecordsRefreshStatus.Idle, requestId = 4),
            loading.cancel(),
        )
    }

    private fun context(
        sourceKey: String = "online",
        clientContextGeneration: Long = 1,
        filter: MemoListFilter = MemoListFilter.Unarchived,
        cacheGeneration: Long = 1,
        paginationRequestId: Long = 1,
    ): RecordsRefreshContext {
        return RecordsRefreshContext(
            sourceKey = sourceKey,
            clientContextGeneration = clientContextGeneration,
            filter = filter,
            cacheGeneration = cacheGeneration,
            paginationRequestId = paginationRequestId,
        )
    }
}
