package app.sillage.core.application.settings

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals

class AIProfileDiagnosticsTest {
    @Test
    fun focusedUseCasesDelegateToIndependentCapabilities() {
        val configuration = configuration()
        val tester = CapturingTester()
        val catalog = CapturingCatalog()

        val model = runAIProfileDiagnosticsSuspend {
            TestAIProfileConnectionUseCase(tester)(configuration)
        }
        val models = runAIProfileDiagnosticsSuspend {
            ListAIProfileModelsUseCase(catalog)(configuration)
        }

        assertEquals("connected-model", model)
        assertEquals(listOf("model-a", "model-b"), models)
        assertEquals(configuration, tester.configuration)
        assertEquals(configuration, catalog.configuration)
    }

    private class CapturingTester : AIProfileConnectionTester {
        var configuration: AIProfileConfigurationCommand? = null

        override suspend fun test(configuration: AIProfileConfigurationCommand): String {
            this.configuration = configuration
            return "connected-model"
        }
    }

    private class CapturingCatalog : AIProfileModelCatalog {
        var configuration: AIProfileConfigurationCommand? = null

        override suspend fun listModels(
            configuration: AIProfileConfigurationCommand,
        ): List<String> {
            this.configuration = configuration
            return listOf("model-a", "model-b")
        }
    }

    private fun configuration(): AIProfileConfigurationCommand = AIProfileConfigurationCommand(
        id = null,
        name = "Draft",
        provider = "anthropic",
        baseUrl = "https://example.test",
        model = "model",
        temperature = 0.3,
        maxTokens = 1000,
        storedTemperature = 0.3,
        storedMaxTokens = 1000,
        enabled = true,
        active = true,
        hasApiKey = false,
        keyUnavailable = false,
        apiKey = "secret",
    )
}

private fun <T> runAIProfileDiagnosticsSuspend(block: suspend () -> T): T {
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
