package app.sillage.core.application.ask

import app.sillage.core.domain.ask.AskSourceRef

data class AppendAskTurnCommand(
    val conversationId: String,
    val question: String,
    val answer: String,
    val sourceRefs: List<AskSourceRef>,
    val model: String,
    val promptVersion: String,
    val parentMessageId: String?,
    val forkOfMessageId: String?,
)

fun interface AskTurnStore {
    suspend fun append(command: AppendAskTurnCommand)
}

class AppendAskTurnUseCase(
    private val store: AskTurnStore,
) {
    suspend operator fun invoke(command: AppendAskTurnCommand) {
        require(command.conversationId.isNotBlank()) { "conversationId must not be blank" }
        require(command.question.isNotBlank()) { "question must not be blank" }
        require(command.answer.isNotBlank()) { "answer must not be blank" }
        store.append(command)
    }
}
