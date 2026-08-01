package app.sillage.data

import app.sillage.core.application.settings.AIProfileConfiguration
import app.sillage.core.application.settings.AISettingsRepository
import app.sillage.core.application.settings.AISettingsSnapshot

class RemoteAISettingsRepository(
    private val api: SillageApi,
) : AISettingsRepository {
    override suspend fun load(): AISettingsSnapshot {
        val settings = api.getAISettings()
        return AISettingsSnapshot(
            profiles = settings.profiles.map { AIProfileConfiguration(it) },
            autoSummary = settings.autoSummary,
        )
    }
}
