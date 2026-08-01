package app.sillage.core.application.ask

import app.sillage.core.application.settings.AIProfileConfigurationCommand
import app.sillage.core.domain.ask.AskMessage
import app.sillage.core.domain.ask.AskSourceRef
import app.sillage.core.domain.records.Memo

data class GenerateAskAnswerCommand(
    val profile: AIProfileConfigurationCommand,
    val question: String,
    val contextScope: String,
    val records: List<Memo>,
    val history: List<AskMessage>,
)

data class GeneratedAskAnswer(
    val content: String,
    val sourceRefs: List<AskSourceRef>,
    val model: String,
    val promptVersion: String,
)

fun interface AskAnswerGenerator {
    suspend fun generate(command: GenerateAskAnswerCommand): GeneratedAskAnswer
}

class GenerateAskAnswerUseCase(
    private val generator: AskAnswerGenerator,
) {
    suspend operator fun invoke(command: GenerateAskAnswerCommand): GeneratedAskAnswer {
        require(command.question.isNotBlank()) { "question must not be blank" }
        return generator.generate(command)
    }
}
