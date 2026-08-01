package app.sillage.core.application.settings

interface AIAutoSummaryRepository {
    suspend fun save(enabled: Boolean): Boolean
}

class SetAIAutoSummaryUseCase(
    private val repository: AIAutoSummaryRepository,
) {
    suspend operator fun invoke(enabled: Boolean): Boolean = repository.save(enabled)
}
