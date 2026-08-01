package app.sillage.features.ask

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AskSourceNavigationStateHolderTest {
    @Test
    fun requestCapturesNavigationAndClientContext() {
        val context = context()
        val idle = AskSourceNavigationStateHolder(requestId = 3)
        val request = assertNotNull(idle.nextRequest("memo-1", context))
        val pending = assertNotNull(idle.begin(request, context))

        assertEquals(4, request.requestId)
        assertEquals(listOf("Memos", "Ask"), request.destinationHistoryKeys())
        assertTrue(pending.canApply(request, context))
        assertNull(pending.nextRequest("memo-2", context))
    }

    @Test
    fun responseCannotCrossNavigationOrConversation() {
        val context = context()
        val idle = AskSourceNavigationStateHolder()
        val request = assertNotNull(idle.nextRequest("memo-1", context))
        val pending = assertNotNull(idle.begin(request, context))

        assertFalse(pending.canApply(request, context.copy(destinationKey = "Settings")))
        assertFalse(pending.canApply(request, context.copy(historyKeys = emptyList())))
        assertFalse(pending.canApply(request, context.copy(conversationId = "ask-2")))
    }

    @Test
    fun staleContextCanClearOnlyMatchingBusyRequest() {
        val context = context()
        val idle = AskSourceNavigationStateHolder()
        val request = assertNotNull(idle.nextRequest("memo-1", context))
        val pending = assertNotNull(idle.begin(request, context))

        assertFalse(assertNotNull(pending.finish(request)).loading)
        assertNull(pending.finish(request.copy(requestId = request.requestId + 1)))
    }

    @Test
    fun unavailableBusyOrBlankRequestDoesNotStart() {
        val idle = AskSourceNavigationStateHolder()

        assertNull(idle.nextRequest("", context()))
        assertNull(idle.nextRequest("memo-1", context(destinationAvailable = false)))
        assertNull(idle.nextRequest("memo-1", context(anotherRequestInProgress = true)))
    }

    private fun context(
        destinationAvailable: Boolean = true,
        anotherRequestInProgress: Boolean = false,
    ) = AskSourceNavigationContext(
        destinationKey = "Ask",
        destinationAvailable = destinationAvailable,
        historyKeys = listOf("Memos"),
        anotherRequestInProgress = anotherRequestInProgress,
        screenSessionId = 2,
        conversationId = "ask-1",
        appMode = "online",
        clientContextGeneration = 5,
    )
}
