package app.sillage.data

import app.sillage.core.application.settings.AIProfileSaveCommand
import app.sillage.core.application.settings.AIProfilesRepository
import app.sillage.core.domain.settings.AIProfile

class RemoteAIProfilesRepository(
    private val api: SillageApi,
) : AIProfilesRepository {
    override suspend fun save(profiles: List<AIProfileSaveCommand>): List<AIProfile> {
        return api.patchAISettings(profiles.map { it.toRemoteInput() }).profiles
    }
}

private fun AIProfileSaveCommand.toRemoteInput(): AIProfileInput {
    return AIProfileInput(
        id = id,
        name = name,
        provider = provider,
        baseUrl = baseUrl,
        model = model,
        temperature = temperature,
        maxTokens = maxTokens,
        enabled = enabled,
        active = active,
        apiKey = apiKey,
    )
}
