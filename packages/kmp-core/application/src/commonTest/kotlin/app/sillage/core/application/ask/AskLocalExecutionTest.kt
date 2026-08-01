package app.sillage.core.application.ask

import app.sillage.core.application.settings.AIProfileConfigurationCommand
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals

class AskLocalExecutionTest {
    @Test
    fun generatorAndTurnStoreRemainIndependentCapabilities() {
        val generationCommand = GenerateAskAnswerCommand(
            profile = profile(),
            question = "Question",
            contextScope = "recent",
            records = emptyList(),
            history = emptyList(),
        )
        var capturedGeneration: GenerateAskAnswerCommand? = null
        val generator = AskAnswerGenerator { command ->
            capturedGeneration = command
            GeneratedAskAnswer("Answer", emptyList(), "model", "prompt-v1")
        }
        var capturedTurn: AppendAskTurnCommand? = null
        val store = AskTurnStore { capturedTurn = it }

        val answer = runAskLocalExecutionSuspend {
            GenerateAskAnswerUseCase(generator)(generationCommand)
        }
        val turn = AppendAskTurnCommand(
            conversationId = "ask-1",
            question = generationCommand.question,
            answer = answer.content,
            sourceRefs = answer.sourceRefs,
            model = answer.model,
            promptVersion = answer.promptVersion,
            parentMessageId = null,
            forkOfMessageId = null,
        )
        runAskLocalExecutionSuspend { AppendAskTurnUseCase(store)(turn) }

        assertEquals(generationCommand, capturedGeneration)
        assertEquals(turn, capturedTurn)
    }

    private fun profile(): AIProfileConfigurationCommand = AIProfileConfigurationCommand(
        id = "profile-1",
        name = "Primary",
        provider = "anthropic",
        baseUrl = "https://example.test",
        model = "model",
        temperature = 0.3,
        maxTokens = 1000,
        storedTemperature = 0.3,
        storedMaxTokens = 1000,
        enabled = true,
        active = true,
        hasApiKey = true,
        keyUnavailable = false,
        apiKey = "secret",
    )
}

private fun <T> runAskLocalExecutionSuspend(block: suspend () -> T): T {
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
