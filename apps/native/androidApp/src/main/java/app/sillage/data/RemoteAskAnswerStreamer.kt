package app.sillage.data

import app.sillage.core.application.ask.AskAnswerStreamEvent
import app.sillage.core.application.ask.AskAnswerStreamer
import app.sillage.core.application.ask.StreamAskAnswerCommand

class RemoteAskAnswerStreamer(
    private val api: SillageApi,
) : AskAnswerStreamer {
    override suspend fun stream(
        command: StreamAskAnswerCommand,
        onEvent: (AskAnswerStreamEvent) -> Unit,
    ) {
        api.streamAskMessage(
            conversationId = command.conversationId,
            content = command.content,
            contextScope = command.contextScope,
            sourceKind = command.sourceKind,
            forkOfId = command.forkOfMessageId,
            onStart = { userMessage, regenerating ->
                onEvent(AskAnswerStreamEvent.Started(userMessage, regenerating))
            },
            onDelta = { onEvent(AskAnswerStreamEvent.Delta(it)) },
            onError = { onEvent(AskAnswerStreamEvent.Failed(it)) },
        )
    }
}
