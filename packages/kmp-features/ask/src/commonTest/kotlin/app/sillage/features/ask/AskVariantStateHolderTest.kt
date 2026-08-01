package app.sillage.features.ask

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AskVariantStateHolderTest {
    @Test
    fun requestIsSingleFlightAndCapturesSelectionContext() {
        val idle = AskVariantStateHolder(requestId = 4)
        val context = context()
        val request = assertNotNull(idle.nextRequest(context))
        val pending = assertNotNull(idle.begin(request, context))

        assertEquals(5, request.requestId)
        assertEquals("ask-1", request.conversationId)
        assertTrue(pending.canApply(request, context))
        assertNull(pending.nextRequest(context))
    }

    @Test
    fun responseCannotCrossConversationOrClientContext() {
        val context = context()
        val idle = AskVariantStateHolder()
        val request = assertNotNull(idle.nextRequest(context))
        val pending = assertNotNull(idle.begin(request, context))

        assertFalse(pending.canApply(request, context.copy(conversationId = "ask-2")))
        assertFalse(
            pending.canApply(
                request,
                context.copy(clientContextGeneration = context.clientContextGeneration + 1),
            ),
        )
        assertNull(pending.finish(request, context.copy(screenSessionId = 8)))
    }

    @Test
    fun invalidationSupersedesPendingRequest() {
        val context = context()
        val idle = AskVariantStateHolder()
        val request = assertNotNull(idle.nextRequest(context))
        val pending = assertNotNull(idle.begin(request, context))

        val invalidated = pending.invalidate()

        assertFalse(invalidated.loading)
        assertEquals(request.requestId + 1, invalidated.requestId)
        assertFalse(invalidated.canApply(request, context))
    }

    @Test
    fun unavailableOrBusyContextCannotStartRequest() {
        val idle = AskVariantStateHolder()

        assertNull(idle.nextRequest(context(destinationAvailable = false)))
        assertNull(idle.nextRequest(context(conversationId = "")))
        assertNull(idle.nextRequest(context(anotherRequestInProgress = true)))
    }

    private fun context(
        destinationAvailable: Boolean = true,
        conversationId: String = "ask-1",
        anotherRequestInProgress: Boolean = false,
    ) = AskVariantContext(
        destinationAvailable = destinationAvailable,
        screenSessionId = 7,
        conversationId = conversationId,
        appMode = "offline",
        clientContextGeneration = 3,
        anotherRequestInProgress = anotherRequestInProgress,
    )
}
