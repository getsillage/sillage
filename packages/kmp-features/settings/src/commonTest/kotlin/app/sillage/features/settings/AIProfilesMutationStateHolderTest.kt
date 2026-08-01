package app.sillage.features.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AIProfilesMutationStateHolderTest {
    private val context = AIProfilesMutationContext(
        appMode = "online",
        clientContextGeneration = 3,
        anotherOperationInProgress = false,
    )

    @Test
    fun mutationIsSingleFlightAndValidatesClientContext() {
        val original = listOf(AIProfileDraft(id = "p1", name = "Original"))
        val pending = listOf(AIProfileDraft(id = "p1", name = "Pending"))
        val idle = AIProfilesMutationStateHolder(profiles = original, requestId = 4)
        val request = requireNotNull(idle.nextRequest(pending, context))
        val saving = requireNotNull(idle.begin(request, context))

        assertEquals(5, request.requestId)
        assertEquals(pending, saving.profiles)
        assertTrue(saving.saving)
        assertNull(saving.nextRequest(original, context))
        assertTrue(saving.canApply(request, context))
        assertFalse(
            saving.canApply(
                request,
                context.copy(clientContextGeneration = context.clientContextGeneration + 1),
            ),
        )
    }

    @Test
    fun completionAndFailureOnlyReplaceTheirOwnOptimisticSnapshot() {
        val original = listOf(AIProfileDraft(id = "p1", name = "Original"))
        val pending = listOf(AIProfileDraft(id = "p1", name = "Pending"))
        val saved = listOf(AIProfileDraft(id = "p1", name = "Saved"))
        val idle = AIProfilesMutationStateHolder(profiles = original)
        val request = requireNotNull(idle.nextRequest(pending, context))
        val saving = requireNotNull(idle.begin(request, context))

        assertEquals(saved, requireNotNull(saving.complete(request, saved, context)).profiles)
        assertEquals(original, requireNotNull(saving.fail(request, context)).profiles)

        val editedAgain = saving.replace(listOf(AIProfileDraft(id = "p1", name = "Later edit")))
        assertEquals(editedAgain.profiles, requireNotNull(editedAgain.fail(request, context)).profiles)
    }

    @Test
    fun invalidateCancelsPendingCallbacksAndAdvancesGeneration() {
        val pending = listOf(AIProfileDraft(id = "p1", name = "Pending"))
        val idle = AIProfilesMutationStateHolder()
        val request = requireNotNull(idle.nextRequest(pending, context))
        val saving = requireNotNull(idle.begin(request, context))
        val replacement = listOf(AIProfileDraft(id = "p2", name = "Replacement"))

        val invalidated = saving.invalidate(replacement)

        assertEquals(replacement, invalidated.profiles)
        assertFalse(invalidated.saving)
        assertEquals(request.requestId + 1, invalidated.requestId)
        assertFalse(invalidated.canApply(request, context))
    }

    @Test
    fun anotherOperationBlocksMutation() {
        assertNull(
            AIProfilesMutationStateHolder().nextRequest(
                pendingProfiles = emptyList(),
                context = context.copy(anotherOperationInProgress = true),
            ),
        )
    }
}
