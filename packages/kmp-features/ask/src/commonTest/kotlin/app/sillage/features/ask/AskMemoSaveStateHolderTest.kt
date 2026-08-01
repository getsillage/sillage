package app.sillage.features.ask

import app.sillage.core.domain.ask.AskMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AskMemoSaveStateHolderTest {
    @Test
    fun requestCapturesAnswerAndSelectionContext() {
        val answer = message(content = "Answer")
        val context = context(messages = listOf(answer))
        val idle = AskMemoSaveStateHolder(requestId = 2)
        val request = assertNotNull(idle.nextRequest(answer, "Memo", context))
        val pending = assertNotNull(idle.begin(request, context))

        assertEquals(3, request.requestId)
        assertEquals("Answer", request.sourceMessageContent)
        assertEquals("Memo", request.memoContent)
        assertTrue(pending.canApply(request, context))
        assertNull(pending.nextRequest(answer, "Memo", context))
    }

    @Test
    fun changedAnswerOrBranchRejectsLateCompletion() {
        val answer = message(content = "Answer")
        val context = context(messages = listOf(answer))
        val idle = AskMemoSaveStateHolder()
        val request = assertNotNull(idle.nextRequest(answer, "Memo", context))
        val pending = assertNotNull(idle.begin(request, context))

        assertFalse(
            pending.canApply(
                request,
                context.copy(messages = listOf(answer.copy(content = "Changed"))),
            ),
        )
        assertFalse(pending.canApply(request, context.copy(headMessageId = "answer-2")))
    }

    @Test
    fun staleRequestCanClearOnlyItsOwnBusyMarker() {
        val answer = message(content = "Answer")
        val context = context(messages = listOf(answer))
        val idle = AskMemoSaveStateHolder()
        val request = assertNotNull(idle.nextRequest(answer, "Memo", context))
        val pending = assertNotNull(idle.begin(request, context))

        assertEquals("", assertNotNull(pending.finish(request)).savingMessageId)
        assertNull(pending.finish(request.copy(requestId = request.requestId + 1)))
    }

    @Test
    fun nonAssistantOrForeignMessageCannotStartRequest() {
        val answer = message(content = "Answer")
        val context = context(messages = listOf(answer))
        val idle = AskMemoSaveStateHolder()

        assertNull(idle.nextRequest(answer.copy(role = "user"), "Memo", context))
        assertNull(
            idle.nextRequest(
                answer.copy(conversationId = "ask-2"),
                "Memo",
                context,
            ),
        )
        assertNull(idle.nextRequest(answer, "", context))
    }

    private fun context(messages: List<AskMessage>) = AskMemoSaveContext(
        destinationAvailable = true,
        anotherRequestInProgress = false,
        screenSessionId = 4,
        conversationId = "ask-1",
        headMessageId = "answer-1",
        messages = messages,
        appMode = "offline",
        clientContextGeneration = 2,
    )

    private fun message(content: String) = AskMessage(
        id = "answer-1",
        conversationId = "ask-1",
        role = "assistant",
        content = content,
        parentId = null,
        forkOfId = null,
        status = "complete",
        sourceRefs = emptyList(),
        model = "model",
        promptVersion = "v1",
        createdAt = "2026-08-01T00:00:00Z",
        updatedAt = "2026-08-01T00:00:00Z",
        deletedAt = null,
    )
}
