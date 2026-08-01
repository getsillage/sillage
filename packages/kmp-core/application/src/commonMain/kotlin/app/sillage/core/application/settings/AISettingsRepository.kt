package app.sillage.core.application.settings

import app.sillage.core.domain.settings.AIProfile

/** Canonical profile metadata plus an optional secret available to this device. */
data class AIProfileConfiguration(
    val profile: AIProfile,
    val apiKey: String? = null,
)

/** Consistent editable settings snapshot returned by one repository read. */
data class AISettingsSnapshot(
    val profiles: List<AIProfileConfiguration>,
    val autoSummary: Boolean,
)

interface AISettingsRepository {
    suspend fun load(): AISettingsSnapshot
}

class LoadAISettingsUseCase(
    private val repository: AISettingsRepository,
) {
    suspend operator fun invoke(): AISettingsSnapshot = repository.load()
}
