package app.sillage.core.application.settings

import app.sillage.core.domain.settings.AIProfile
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals

class AISettingsRepositoryTest {
    @Test
    fun loadUseCaseReturnsOneRepositorySnapshot() {
        val expected = AISettingsSnapshot(
            profiles = listOf(AIProfileConfiguration(profile(), apiKey = "local-secret")),
            autoSummary = true,
        )
        val repository = object : AISettingsRepository {
            override suspend fun load(): AISettingsSnapshot = expected
        }

        val actual = runAISettingsSuspend { LoadAISettingsUseCase(repository)() }

        assertEquals(expected, actual)
        assertEquals("profile-1", actual.profiles.single().toCommand().id)
        assertEquals("local-secret", actual.profiles.single().toCommand().apiKey)
        assertEquals("profile-1", actual.activeProfileOrNull()?.profile?.id)
    }

    private fun profile(): AIProfile = AIProfile(
        id = "profile-1",
        name = "Primary",
        provider = "anthropic",
        baseUrl = "https://example.test",
        model = "model",
        temperature = 0.3,
        maxTokens = 1000,
        enabled = true,
        active = true,
        hasApiKey = true,
        keyUnavailable = false,
        autoSummary = true,
        createdAt = "",
        updatedAt = "",
    )
}

private fun <T> runAISettingsSuspend(block: suspend () -> T): T {
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
