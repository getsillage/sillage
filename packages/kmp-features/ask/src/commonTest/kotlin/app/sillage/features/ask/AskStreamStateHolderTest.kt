package app.sillage.features.ask

import app.sillage.core.domain.ask.AskMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AskStreamStateHolderTest {
    @Test
    fun requestIsSingleFlightAndBoundToClientContext() {
        val context = context()
        val idle = AskStreamStateHolder(requestId = 4)
        val request = assertNotNull(idle.nextRequest(context))
        val pending = assertNotNull(idle.begin(request, context, "answer-1"))

        assertEquals(5, request.requestId)
        assertEquals("answer-1", pending.regeneratingMessageId)
        assertTrue(pending.canApply(request, context))
        assertNull(pending.nextRequest(context))
        assertFalse(
            pending.canApply(
                request,
                context.copy(clientContextGeneration = context.clientContextGeneration + 1),
            ),
        )
    }

    @Test
    fun streamingBuffersAnswerAndClearsTransientPresentation() {
        val user = message()
        val streaming = AskStreamStateHolder(sending = true)
            .startStreaming(user)
            .appendDelta("first ")
            .appendDelta("second")

        assertTrue(streaming.streaming)
        assertEquals(user, streaming.liveUser)
        assertEquals("first second", streaming.liveAnswer)

        val finished = streaming.finish(answerCompleted = true)
        assertFalse(finished.sending)
        assertFalse(finished.streaming)
        assertEquals("", finished.liveAnswer)
        assertEquals(1, finished.completionEventId)
    }

    @Test
    fun incompleteAnswerDoesNotPublishCompletionEvent() {
        val finished = AskStreamStateHolder(
            sending = true,
            completionEventId = 7,
        ).finish(answerCompleted = false)

        assertEquals(7, finished.completionEventId)
    }

    @Test
    fun invalidationSupersedesPendingRequest() {
        val context = context()
        val idle = AskStreamStateHolder()
        val request = assertNotNull(idle.nextRequest(context))
        val pending = assertNotNull(idle.begin(request, context, ""))

        val invalidated = pending.invalidate()

        assertEquals(request.requestId + 1, invalidated.requestId)
        assertFalse(invalidated.canApply(request, context))
    }

    private fun context() = AskStreamContext(
        screenSessionId = 3,
        conversationId = "ask-1",
        appMode = "online",
        clientContextGeneration = 6,
        anotherRequestInProgress = false,
    )

    private fun message() = AskMessage(
        id = "question-1",
        conversationId = "ask-1",
        role = "user",
        content = "Question",
        parentId = null,
        forkOfId = null,
        status = "complete",
        sourceRefs = emptyList(),
        model = "",
        promptVersion = "v1",
        createdAt = "2026-08-01T00:00:00Z",
        updatedAt = "2026-08-01T00:00:00Z",
        deletedAt = null,
    )
}
