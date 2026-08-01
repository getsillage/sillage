package app.sillage.features.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AISettingsLoadStateHolderTest {
    private val context = AISettingsLoadContext(
        appMode = "online",
        clientContextGeneration = 2,
        anotherOperationInProgress = false,
    )

    @Test
    fun loadIsSingleFlightAndContextBound() {
        val idle = AISettingsLoadStateHolder(requestId = 4)
        val request = requireNotNull(idle.nextRequest(context))
        val loading = requireNotNull(idle.begin(request, context))

        assertEquals(5, request.requestId)
        assertTrue(loading.loading)
        assertNull(loading.nextRequest(context))
        assertTrue(loading.canApply(request, context))
        assertFalse(loading.canApply(request, context.copy(appMode = "offline")))
    }

    @Test
    fun completionFailureAndCancellationRejectLateResponses() {
        val idle = AISettingsLoadStateHolder()
        val request = requireNotNull(idle.nextRequest(context))
        val loading = requireNotNull(idle.begin(request, context))

        val completed = requireNotNull(loading.complete(request, context))
        assertFalse(completed.loading)
        assertNull(completed.errorMessage)

        val failed = requireNotNull(loading.fail(request, "Unavailable", context))
        assertFalse(failed.loading)
        assertEquals("Unavailable", failed.errorMessage)

        val cancelled = loading.cancel()
        assertEquals(request.requestId + 1, cancelled.requestId)
        assertFalse(cancelled.canApply(request, context))
        assertNull(cancelled.errorMessage)
    }

    @Test
    fun anotherOperationBlocksLoad() {
        assertNull(
            AISettingsLoadStateHolder().nextRequest(
                context.copy(anotherOperationInProgress = true),
            ),
        )
    }
}
