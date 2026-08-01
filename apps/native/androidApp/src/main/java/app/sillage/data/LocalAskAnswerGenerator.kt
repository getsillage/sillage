package app.sillage.data

import app.sillage.core.application.ask.AskAnswerGenerator
import app.sillage.core.application.ask.GenerateAskAnswerCommand
import app.sillage.core.application.ask.GeneratedAskAnswer

class LocalAskAnswerGenerator(
    private val client: LocalAiClient,
) : AskAnswerGenerator {
    override suspend fun generate(command: GenerateAskAnswerCommand): GeneratedAskAnswer {
        val answer = client.answerQuestion(
            profile = command.profile.toLocalDraft(),
            question = command.question,
            scope = command.contextScope,
            loadMemos = { command.records },
            history = command.history,
        )
        return GeneratedAskAnswer(
            content = answer.answer,
            sourceRefs = answer.sourceRefs,
            model = answer.model,
            promptVersion = answer.promptVersion,
        )
    }
}
