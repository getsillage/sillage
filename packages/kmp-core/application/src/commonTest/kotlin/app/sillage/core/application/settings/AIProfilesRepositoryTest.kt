package app.sillage.core.application.settings

import app.sillage.core.domain.settings.AIProfile
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals

class AIProfilesRepositoryTest {
    @Test
    fun saveUseCaseDelegatesPlatformNeutralWriteIntent() = runAIProfilesSuspend {
        val command = AIProfileConfigurationCommand(
            id = "profile-1",
            name = "Primary",
            provider = "anthropic",
            baseUrl = "https://example.test",
            model = "model",
            temperature = null,
            maxTokens = 2048,
            storedTemperature = 0.3,
            storedMaxTokens = 2048,
            enabled = true,
            active = true,
            hasApiKey = true,
            keyUnavailable = false,
            apiKey = "secret",
        )
        val expected = profile()
        val repository = CapturingRepository(expected)

        val result = SaveAIProfilesUseCase(repository)(listOf(command))

        assertEquals(listOf(command), repository.commands)
        assertEquals(listOf(expected), result)
    }

    private class CapturingRepository(
        private val result: AIProfile,
    ) : AIProfilesRepository {
        var commands: List<AIProfileConfigurationCommand> = emptyList()

        override suspend fun save(profiles: List<AIProfileConfigurationCommand>): List<AIProfile> {
            commands = profiles
            return listOf(result)
        }
    }

    private fun profile(): AIProfile = AIProfile(
        id = "profile-1",
        name = "Primary",
        provider = "anthropic",
        baseUrl = "https://example.test",
        model = "model",
        temperature = 0.3,
        maxTokens = 2048,
        enabled = true,
        active = true,
        hasApiKey = true,
        keyUnavailable = false,
        autoSummary = false,
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-01T00:00:00Z",
    )
}

private fun <T> runAIProfilesSuspend(block: suspend () -> T): T {
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
