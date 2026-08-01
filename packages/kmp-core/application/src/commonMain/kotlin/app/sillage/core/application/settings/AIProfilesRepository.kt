package app.sillage.core.application.settings

import app.sillage.core.domain.settings.AIProfile

/**
 * Platform-neutral profile write intent.
 *
 * Parsed numeric values remain nullable so transport adapters can preserve the
 * existing omitted-field semantics. Local adapters use the last valid stored
 * values when editor input is temporarily invalid. Secret input is write-only
 * and never appears in the returned domain profile metadata.
 */
data class AIProfileSaveCommand(
    val id: String?,
    val name: String,
    val provider: String,
    val baseUrl: String,
    val model: String,
    val temperature: Double?,
    val maxTokens: Long?,
    val storedTemperature: Double,
    val storedMaxTokens: Long,
    val enabled: Boolean,
    val active: Boolean,
    val hasApiKey: Boolean,
    val keyUnavailable: Boolean,
    val apiKey: String?,
)

interface AIProfilesRepository {
    suspend fun save(profiles: List<AIProfileSaveCommand>): List<AIProfile>
}

class SaveAIProfilesUseCase(
    private val repository: AIProfilesRepository,
) {
    suspend operator fun invoke(profiles: List<AIProfileSaveCommand>): List<AIProfile> {
        return repository.save(profiles)
    }
}
