package app.sillage.data

import app.sillage.core.application.settings.AIProfileConfiguration
import app.sillage.core.application.settings.AISettingsRepository
import app.sillage.core.application.settings.AISettingsSnapshot

class LocalAISettingsRepository(
    private val localDataStore: LocalDataStore,
) : AISettingsRepository {
    override suspend fun load(): AISettingsSnapshot {
        val data = localDataStore.exportData()
        return AISettingsSnapshot(
            profiles = data.aiProfiles.map { draft ->
                AIProfileConfiguration(
                    profile = draft.toDomainProfile(data.autoSummary),
                    apiKey = draft.apiKeyInput.takeIf { it.isNotBlank() },
                )
            },
            autoSummary = data.autoSummary,
        )
    }
}
