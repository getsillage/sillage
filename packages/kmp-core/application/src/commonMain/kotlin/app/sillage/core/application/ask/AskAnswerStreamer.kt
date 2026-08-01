package app.sillage.core.application.ask

import app.sillage.core.domain.ask.AskMessage

data class StreamAskAnswerCommand(
    val conversationId: String,
    val content: String,
    val contextScope: String,
    val sourceKind: String,
    val forkOfMessageId: String? = null,
)

sealed interface AskAnswerStreamEvent {
    data class Started(
        val userMessage: AskMessage,
        val regenerating: Boolean,
    ) : AskAnswerStreamEvent

    data class Delta(val text: String) : AskAnswerStreamEvent

    data class Failed(val message: String) : AskAnswerStreamEvent
}

fun interface AskAnswerStreamer {
    suspend fun stream(
        command: StreamAskAnswerCommand,
        onEvent: (AskAnswerStreamEvent) -> Unit,
    )
}

class StreamAskAnswerUseCase(
    private val streamer: AskAnswerStreamer,
) {
    suspend operator fun invoke(
        command: StreamAskAnswerCommand,
        onEvent: (AskAnswerStreamEvent) -> Unit,
    ) {
        require(command.conversationId.isNotBlank()) { "conversationId must not be blank" }
        require(command.content.isNotBlank()) { "content must not be blank" }
        streamer.stream(command, onEvent)
    }
}
