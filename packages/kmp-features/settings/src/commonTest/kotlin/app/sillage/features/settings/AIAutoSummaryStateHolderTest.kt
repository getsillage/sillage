package app.sillage.features.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AIAutoSummaryStateHolderTest {
    @Test
    fun mutationAppliesOptimisticallyAndCompletesWithPersistedValue() {
        val context = context()
        val idle = AIAutoSummaryStateHolder(requestId = 4)
        val request = assertNotNull(idle.nextRequest(true, context))
        val pending = assertNotNull(idle.begin(request, context))

        assertTrue(pending.enabled)
        assertTrue(pending.saving)
        assertEquals(5, request.requestId)

        val completed = assertNotNull(pending.complete(request, false, context))
        assertFalse(completed.enabled)
        assertFalse(completed.saving)
    }

    @Test
    fun failureRollsBackPreviousValue() {
        val context = context()
        val idle = AIAutoSummaryStateHolder(enabled = true)
        val request = assertNotNull(idle.nextRequest(false, context))
        val pending = assertNotNull(idle.begin(request, context))

        val failed = assertNotNull(pending.fail(request, context))

        assertTrue(failed.enabled)
        assertFalse(failed.saving)
    }

    @Test
    fun lateCompletionCannotCrossClientContext() {
        val context = context()
        val idle = AIAutoSummaryStateHolder()
        val request = assertNotNull(idle.nextRequest(true, context))
        val pending = assertNotNull(idle.begin(request, context))

        assertNull(
            pending.complete(
                request,
                true,
                context.copy(clientContextGeneration = context.clientContextGeneration + 1),
            ),
        )
    }

    @Test
    fun unchangedOrBusyPreferenceDoesNotStartMutation() {
        val idle = AIAutoSummaryStateHolder(enabled = true)

        assertNull(idle.nextRequest(true, context()))
        assertNull(idle.nextRequest(false, context(anotherMutationInProgress = true)))
    }

    private fun context(
        anotherMutationInProgress: Boolean = false,
    ) = AIAutoSummaryContext(
        appMode = "offline",
        clientContextGeneration = 3,
        anotherMutationInProgress = anotherMutationInProgress,
    )
}
