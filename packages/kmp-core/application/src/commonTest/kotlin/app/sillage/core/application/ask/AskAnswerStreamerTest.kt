package app.sillage.core.application.ask

import app.sillage.core.domain.ask.AskMessage
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AskAnswerStreamerTest {
    @Test
    fun useCaseValidatesAndForwardsOrderedEvents() {
        val events = mutableListOf<AskAnswerStreamEvent>()
        val streamer = CapturingStreamer()
        val command = StreamAskAnswerCommand(
            conversationId = "ask-1",
            content = "Question",
            contextScope = "recent",
            sourceKind = "memos",
        )

        runAskStreamSuspend { StreamAskAnswerUseCase(streamer)(command, events::add) }

        assertEquals(command, streamer.command)
        assertEquals(streamer.events, events)
    }

    @Test
    fun useCaseRejectsMissingConversationOrContentBeforeAdapterCall() {
        val streamer = CapturingStreamer()
        assertFailsWith<IllegalArgumentException> {
            runAskStreamSuspend {
                StreamAskAnswerUseCase(streamer)(
                    StreamAskAnswerCommand("", "Question", "recent", "memos"),
                ) {}
            }
        }
        assertFailsWith<IllegalArgumentException> {
            runAskStreamSuspend {
                StreamAskAnswerUseCase(streamer)(
                    StreamAskAnswerCommand("ask-1", " ", "recent", "memos"),
                ) {}
            }
        }
        assertEquals(null, streamer.command)
    }

    private class CapturingStreamer : AskAnswerStreamer {
        var command: StreamAskAnswerCommand? = null
        val events = listOf(
            AskAnswerStreamEvent.Started(message(), regenerating = false),
            AskAnswerStreamEvent.Delta("answer"),
            AskAnswerStreamEvent.Failed("provider warning"),
        )

        override suspend fun stream(
            command: StreamAskAnswerCommand,
            onEvent: (AskAnswerStreamEvent) -> Unit,
        ) {
            this.command = command
            events.forEach(onEvent)
        }
    }

    private companion object {
        fun message(): AskMessage = AskMessage(
            id = "message-1",
            conversationId = "ask-1",
            parentId = null,
            role = "user",
            content = "Question",
            forkOfId = null,
            model = "",
            promptVersion = "",
            sourceRefs = emptyList(),
            status = "complete",
            createdAt = "2026-08-01T00:00:00Z",
            updatedAt = "2026-08-01T00:00:00Z",
            deletedAt = null,
        )
    }
}

private fun <T> runAskStreamSuspend(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(
        object : Continuation<T> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<T>) {
                outcome = result
            }
        },
    )
    return checkNotNull(outcome) { "Test coroutine did not complete synchronously" }.getOrThrow()
}
