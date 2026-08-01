package app.sillage.data

import app.sillage.core.application.settings.AIProfileSaveCommand
import app.sillage.core.application.settings.AIProfilesRepository
import app.sillage.core.domain.settings.AIProfile
import app.sillage.features.settings.AIProfileDraft

class LocalAIProfilesRepository(
    private val localDataStore: LocalDataStore,
) : AIProfilesRepository {
    override suspend fun save(profiles: List<AIProfileSaveCommand>): List<AIProfile> {
        val saved = localDataStore.saveAIProfiles(profiles.map { it.toLocalDraft() })
        val autoSummary = localDataStore.autoSummaryEnabled()
        return saved.map { it.toDomainProfile(autoSummary) }
    }
}

private fun AIProfileSaveCommand.toLocalDraft(): AIProfileDraft {
    return AIProfileDraft(
        id = id.orEmpty(),
        name = name,
        provider = provider,
        baseUrl = baseUrl,
        model = model,
        temperature = temperature ?: storedTemperature,
        maxTokens = maxTokens ?: storedMaxTokens,
        enabled = enabled,
        active = active,
        hasApiKey = hasApiKey,
        keyUnavailable = keyUnavailable,
        apiKeyInput = apiKey.orEmpty(),
    )
}

private fun AIProfileDraft.toDomainProfile(autoSummary: Boolean): AIProfile {
    return AIProfile(
        id = id,
        name = name,
        provider = provider,
        baseUrl = baseUrl,
        model = model,
        temperature = temperature,
        maxTokens = maxTokens,
        enabled = enabled,
        active = active,
        hasApiKey = hasApiKey,
        keyUnavailable = keyUnavailable,
        autoSummary = autoSummary,
        createdAt = "",
        updatedAt = "",
    )
}
