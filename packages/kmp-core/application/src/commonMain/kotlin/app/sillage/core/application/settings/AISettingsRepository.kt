package app.sillage.core.application.settings

import app.sillage.core.domain.settings.AIProfile

/** Canonical profile metadata plus an optional secret available to this device. */
data class AIProfileConfiguration(
    val profile: AIProfile,
    val apiKey: String? = null,
)

fun AIProfileConfiguration.toCommand(): AIProfileConfigurationCommand {
    return AIProfileConfigurationCommand(
        id = profile.id.takeIf { it.isNotBlank() },
        name = profile.name,
        provider = profile.provider,
        baseUrl = profile.baseUrl,
        model = profile.model,
        temperature = profile.temperature,
        maxTokens = profile.maxTokens,
        storedTemperature = profile.temperature,
        storedMaxTokens = profile.maxTokens,
        enabled = profile.enabled,
        active = profile.active,
        hasApiKey = profile.hasApiKey,
        keyUnavailable = profile.keyUnavailable,
        apiKey = apiKey,
    )
}

/** Consistent editable settings snapshot returned by one repository read. */
data class AISettingsSnapshot(
    val profiles: List<AIProfileConfiguration>,
    val autoSummary: Boolean,
)

fun AISettingsSnapshot.activeProfileOrNull(): AIProfileConfiguration? {
    val enabled = profiles.filter { it.profile.enabled }
    return enabled.firstOrNull { it.profile.active } ?: enabled.firstOrNull()
}

interface AISettingsRepository {
    suspend fun load(): AISettingsSnapshot
}

class LoadAISettingsUseCase(
    private val repository: AISettingsRepository,
) {
    suspend operator fun invoke(): AISettingsSnapshot = repository.load()
}
